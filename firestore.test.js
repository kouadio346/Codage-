const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPrototypePayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "Dashboard SaaS Interactif",
    sourceLabel: "Maquette SaaS",
    htmlCode: "<main class='card'><h1>ProtoSnap</h1><button id='cta'>Action</button></main>",
    cssCode: ".card { padding: 24px; border-radius: 16px; }",
    jsCode: "document.getElementById('cta').addEventListener('click', () => console.log('clicked'));",
    aiNotes: "Bouton interactif optimisé avec écouteur d'événement.",
    interactiveFeatures: ["Bouton CTA actif", "Layout responsive"],
    favorite: false,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write prototypes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/prototypes`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/prototypes`)
      .doc("proto_1")
      .set(validPrototypePayload(ALICE_UID))
  );
});

test("Owner can create, list, get, partially update, and delete prototype", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/prototypes`).doc("proto_1");

  await assertSucceeds(ref.set(validPrototypePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/prototypes`).get());
  await assertSucceeds(ref.get());

  await assertSucceeds(
    ref.update({
      title: "Dashboard SaaS v2",
      jsCode: "console.log('updated');",
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(ref.delete());
});

test("Cross-user access is denied for get, list, create, update, and delete", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection(`users/${BOB_UID}/prototypes`)
      .doc("bob_proto")
      .set(validPrototypePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/prototypes`).get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/prototypes`).doc("bob_proto").get()
  );
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/prototypes`)
      .doc("bob_proto")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/prototypes`)
      .doc("alice_in_bob")
      .set(validPrototypePayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/prototypes`).doc("bob_proto").delete()
  );
});

test("Schema validation rejects extra id field, invalid title, future timestamp, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection(`users/${ALICE_UID}/prototypes`);

  // Reject extra @DocumentId 'id' field
  await assertFails(
    col.doc("proto_extra").set({
      ...validPrototypePayload(ALICE_UID),
      id: "proto_extra",
    })
  );

  // Reject blank title
  await assertFails(
    col.doc("proto_blank").set({
      ...validPrototypePayload(ALICE_UID),
      title: "",
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    col.doc("proto_future").set(
      validPrototypePayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Create valid doc, then verify immutable fields cannot change
  await assertSucceeds(col.doc("proto_immut").set(validPrototypePayload(ALICE_UID)));
  await assertFails(
    col.doc("proto_immut").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    col.doc("proto_immut").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
