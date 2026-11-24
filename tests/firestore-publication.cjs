const fs = require("node:fs");
const assert = require("node:assert/strict");
const { initializeTestEnvironment, assertSucceeds, assertFails } =
  require("@firebase/rules-unit-testing");

async function main() {
  const env = await initializeTestEnvironment({
    projectId: "demo-sequmio",
    firestore: { rules: fs.readFileSync("firestore.rules", "utf8") }
  });
  let count = 0;
  async function check(name, fn) {
    await fn();
    console.log("PASS | " + name);
    count++;
  }
  try {
    await env.clearFirestore();
    const dev = env.authenticatedContext("developer").firestore();
    const other = env.authenticatedContext("other").firestore();
    const guest = env.unauthenticatedContext().firestore();
    const pkg = "com.example.test";
    const path = "apps/" + pkg;
    const scenario = JSON.stringify({test: "First publication atomic transaction fixture"});
    const app = {
      packageName: pkg, name: "QA App", description: "Test fixture",
      ownerUid: "developer", ownerName: "Developer",
      googleGroupUrl: "https://groups.google.com/g/test",
      optInUrl: "https://play.google.com/apps/testing/" + pkg,
      playStoreUrl: "https://play.google.com/store/apps/details?id=" + pkg,
      scenarioJson: scenario, status: "published", updatedAt: new Date(),
      scenarioVersion: 1
    };
    const snapshot = version => ({
      ownerUid: "developer", appId: pkg, version,
      scenarioJson: scenario, suiteNames: ["Smoke"], updatedAt: new Date()
    });
    await check("PUB-01 authenticated missing document get", async () => {
      const doc = await assertSucceeds(dev.doc(path).get());
      assert.equal(doc.exists, false);
    });
    await check("PUB-02 guest missing document denied", () =>
      assertFails(guest.doc(path).get()));
    await check("PUB-03 first publication transaction with version snapshot", () =>
      assertSucceeds(dev.runTransaction(async tx => {
        const existing = await tx.get(dev.doc(path));
        assert.equal(existing.exists, false);
        tx.set(dev.doc(path), app);
        tx.set(dev.doc(path + "/scenarioVersions/v000001"), snapshot(1));
      })));
    await check("PUB-04 published listing visible to guest", () =>
      assertSucceeds(guest.doc(path).get()));
    await check("PUB-05 owner reads first scenario version", () =>
      assertSucceeds(dev.doc(path + "/scenarioVersions/v000001").get()));
    await check("PUB-06 other user cannot overwrite listing", () =>
      assertFails(other.doc(path).set({...app, ownerUid: "other"})));
    await check("PUB-07 owner can publish version 2 atomically", () =>
      assertSucceeds(dev.runTransaction(async tx => {
        const doc = await tx.get(dev.doc(path));
        assert.equal(doc.data().scenarioVersion, 1);
        tx.update(dev.doc(path), {scenarioVersion: 2, updatedAt: new Date()});
        tx.set(dev.doc(path + "/scenarioVersions/v000002"), snapshot(2));
      })));
    await check("PUB-08 owner unpublishes", () =>
      assertSucceeds(dev.doc(path).update({status: "draft"})));
    await check("PUB-09 other user cannot read existing draft", () =>
      assertFails(other.doc(path).get()));
    await check("PUB-10 guest cannot read existing draft", () =>
      assertFails(guest.doc(path).get()));
    await check("PUB-11 owner can read existing draft", () =>
      assertSucceeds(dev.doc(path).get()));
    await check("PUB-12 other user cannot list private drafts", () =>
      assertFails(other.collection("apps").where("status", "==", "draft").get()));
    console.log("RESULT: " + count + "/12 PASSED");
  } finally { await env.cleanup(); }
}
main().catch(e => { console.error(e); process.exitCode = 1; });
