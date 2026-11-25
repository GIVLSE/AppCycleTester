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
    await check("ICON-01 owner adds optional icon", () =>
      assertSucceeds(dev.doc(path).update({iconPng: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"})));
    await check("ICON-02 nonowner cannot change icon", () =>
      assertFails(other.doc(path).update({iconPng: "different"})));
    await check("ICON-03 oversized icon rejected", () =>
      assertFails(dev.doc(path).update({iconPng: "A".repeat(65537)})));
    await check("ICON-04 non-string icon rejected", () =>
      assertFails(dev.doc(path).update({iconPng: 123})));
    await check("ICON-05 published icon readable by guest", async () => {
      await assertSucceeds(dev.doc(path).update({status: "published"}));
      const doc = await assertSucceeds(guest.doc(path).get());
      assert.equal(doc.get("iconPng"), "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB");
    });
    const membership = {userUid: "other", packageName: pkg, selfConfirmed: true, joinedAt: new Date()};
    await check("QA-01 tester joins", () =>
      assertSucceeds(other.doc("memberships/other_" + pkg).set(membership)));
    const query = db => db.collection("memberships")
      .where("packageName", "==", pkg).where("selfConfirmed", "==", true);
    await check("QA-02 listing owner can query confirmed memberships", async () => {
      const result = await assertSucceeds(query(dev).get());
      assert.equal(result.size, 1);
    });
    const stranger = env.authenticatedContext("stranger").firestore();
    await check("QA-03 other developer cannot query memberships", () => assertFails(query(stranger).get()));
    await check("QA-04 guest cannot query memberships", () => assertFails(query(guest).get()));
    await check("QA-05 owner cannot list unrestricted memberships", () =>
      assertFails(dev.collection("memberships").get()));
    await check("QA-06 owner cannot modify tester membership", () =>
      assertFails(dev.doc("memberships/other_" + pkg).delete()));
    await check("QA-07 tester can still read own membership", () =>
      assertSucceeds(other.doc("memberships/other_" + pkg).get()));
    console.log("RESULT: " + count + "/24 PASSED");
  } finally { await env.cleanup(); }
}
main().catch(e => { console.error(e); process.exitCode = 1; });
