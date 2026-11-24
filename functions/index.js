"use strict";
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp, FieldPath } = require("firebase-admin/firestore");
initializeApp();
const db = getFirestore();
const mailKey = defineSecret("SEQ_REPORT_EMAIL_API_KEY");

function priorUtcDay(now = new Date()) {
  const startOfToday = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const end = new Date(startOfToday);
  const start = new Date(startOfToday - 86400000);
  return { start, end, day: start.toISOString().slice(0, 10) };
}

function accumulate(rows, day) {
  const groups = new Map();
  for (const doc of rows) {
    const r = doc.data();
    if (!r.ownerUid || !r.appId) continue;
    const key = r.ownerUid + "|" + r.appId;
    if (!groups.has(key)) groups.set(key, {
      ownerUid: r.ownerUid, appId: r.appId, dayUtc: day,
      total: 0, pass: 0, fail: 0, stopped: 0, incomplete: 0,
      testers: new Set(), devices: new Set(), byBuild: {}, bySuite: {},
      byScenario: {}, failedSteps: {},
    });
    const g = groups.get(key);
    g.total++;
    const status = String(r.status || "INCOMPLETE");
    if (status === "PASS") g.pass++;
    else if (status === "FAIL") g.fail++;
    else if (status === "STOPPED") g.stopped++;
    else g.incomplete++;
    if (r.testerUid) g.testers.add(r.testerUid);
    g.devices.add(String(r.deviceManufacturer || "") + "|" +
      String(r.deviceModel || "") + "|" + String(r.androidApi || ""));
    const build = String(r.appVersion || "unknown").slice(0, 50);
    const suite = String(r.suite || "UNSPECIFIED").slice(0, 65);
    const scenario = String(r.scenarioName || "Unnamed").slice(0, 100);
    for (const [groupName, label] of [
      ["byBuild", build], ["bySuite", suite], ["byScenario", scenario]]) {
      if (!g[groupName][label]) g[groupName][label] = { runs: 0, pass: 0, fail: 0 };
      const bucket = g[groupName][label];
      bucket.runs++;
      if (status === "PASS") bucket.pass++;
      if (status === "FAIL") bucket.fail++;
    }
    for (const step of Array.isArray(r.stepResults) ? r.stepResults : []) {
      if (step.status === "FAIL") {
        const signature = String(step.scenarioName || "Unknown").slice(0, 60) +
          " / #" + Number(step.stepIndex || 0) + " " +
          String(step.action || "Unknown").slice(0, 25);
        g.failedSteps[signature] = (g.failedSteps[signature] || 0) + 1;
      }
    }
  }
  return [...groups.values()].map(g => ({
    ownerUid: g.ownerUid, appId: g.appId, dayUtc: g.dayUtc,
    total: g.total, pass: g.pass, fail: g.fail,
    stopped: g.stopped, incomplete: g.incomplete,
    uniqueTesters: g.testers.size, uniqueDevices: g.devices.size,
    byBuild: g.byBuild, bySuite: g.bySuite,
    byScenario: g.byScenario, failedSteps: g.failedSteps,
  }));
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[c]);
}

async function maybeEmail(digest, key) {
  if (!key || !process.env.SEQ_REPORT_SENDER) return false;
  const profile = await db.collection("users").doc(digest.ownerUid).get();
  if (!profile.exists || profile.get("dailyDigest") !== true) return false;
  const email = profile.get("email");
  if (typeof email !== "string" || !email.includes("@")) return false;
  const subject = "Sequmio QA | " + digest.appId + " | " + digest.dayUtc + " UTC";
  const html = "<h2>Sequmio daily QA report</h2>" +
    "<p>Application: " + escapeHtml(digest.appId) + "</p>" +
    "<p>Date: " + escapeHtml(digest.dayUtc) + " (UTC)</p>" +
    "<p>Runs: " + digest.total + "; PASS: " + digest.pass +
    "; FAIL: " + digest.fail + "; Stopped: " + digest.stopped +
    "; Incomplete: " + digest.incomplete + "</p>" +
    "<p>Unique devices: " + digest.uniqueDevices +
    "; testers: " + digest.uniqueTesters + "</p>" +
    "<p>Open Sequmio → My App → QA Dashboard for build comparisons.</p>";
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: "Bearer " + key, "Content-Type": "application/json" },
    body: JSON.stringify({ from: process.env.SEQ_REPORT_SENDER,
      to: [email], subject, html })
  });
  if (!response.ok) throw new Error("Email refused: " + response.status);
  return true;
}

/** Scheduled in-app report, with optional email if a verified sender is configured. */
exports.dailyQaSummary = onSchedule({
  schedule: "15 03 * * *",
  timeZone: "Etc/UTC",
  region: "europe-west1",
  timeoutSeconds: 540,
  memory: "256MiB",
  secrets: [mailKey],
}, async () => {
  const { start, end, day } = priorUtcDay();
  const startTime = Timestamp.fromDate(start);
  const endTime = Timestamp.fromDate(end);
  let cursor = null;
  const rows = [];
  do {
    let query = db.collection("testRuns")
      .where("endedAt", ">=", startTime)
      .where("endedAt", "<", endTime)
      .orderBy("endedAt")
      .orderBy(FieldPath.documentId())
      .limit(400);
    if (cursor) query = query.startAfter(cursor);
    const snapshot = await query.get();
    rows.push(...snapshot.docs);
    cursor = snapshot.size === 400 ? snapshot.docs[snapshot.docs.length - 1] : null;
  } while (cursor && rows.length < 25000);
  const digests = accumulate(rows, day);
  const apiKey = mailKey.value();
  for (const digest of digests) {
    const id = digest.ownerUid + "_" + digest.appId + "_" + digest.dayUtc;
    const doc = db.collection("dailyReports").doc(id);
    const previous = await doc.get();
    const payload = {
      ...digest,
      createdAt: previous.exists ? previous.get("createdAt") : Timestamp.now(),
      generatedAt: Timestamp.now(),
      emailSent: previous.exists ? previous.get("emailSent") === true : false,
    };
    await doc.set(payload);
    if (!payload.emailSent) {
      try {
        const sent = await maybeEmail(digest, apiKey);
        if (sent) await doc.update({ emailSent: true });
      } catch (error) {
        console.error("Daily email rejected", {
          appId: digest.appId, dayUtc: digest.dayUtc, kind: error.name });
      }
    }
  }
  console.log("Sequmio daily summaries", {
    day, groups: digests.length, runsRead: rows.length,
  });
});
exports._test = { priorUtcDay, accumulate, escapeHtml };
