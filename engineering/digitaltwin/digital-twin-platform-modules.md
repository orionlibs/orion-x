#### M02 — Identity (Device, Service, Human, Agent)

| | |
|---|---|
| **Responsibility** | Every entity that touches the twin has a verifiable identity. |
| **Owns** | Device credentials/certs, service principals, agent identities, human identity federation. |
| **Consumes** | `M08` device registry (which device *should* exist). |
| **Produces** | Authenticated principal for `M03`. |

**Must do**
- High-capability devices: X.509 client certs + mutual TLS (App. B).
- Constrained LPWAN devices: unique identifier + secure join yielding session keys — LoRaWAN OTAA `DevEUI`/`AppEUI`/`AppKey` (App. A.6).
- Services authenticate to each other; **no network-location trust** (zero trust, §10.2.2).
- Humans: OIDC federation with role separation.
- Key/cert lifecycle: rotation, renewal before expiry, and fast isolation of a compromised device (§3.6).

**JVM notes** — Spring Security 7 / Spring Authorization Server. mTLS terminates at the broker
(`M11`) or gateway; propagate the verified device identity inward as a `ScopedValue` (final in
Java 25) so it survives virtual-thread hops. Java 25's KDF API (JEP 510, final) is a clean fit for
deriving per-session device keys.

**Traps** — every sensor is an attack vector: an attacker can spoof a sensor or
replay old telemetry without ever touching your cloud — identity at the edge is not optional.

---

#### M03 — Authorization & Policy-as-Code

| | |
|---|---|
| **Responsibility** | Decide whether a principal may perform an action on a resource — including whether an automated action is *allowed to reach the physical world*. |
| **Owns** | Policy documents, versioned; decision log entries. |
| **Consumes** | Principal from `M02`; proposed command + sensor context from `M40`/`M37`. |
| **Produces** | ALLOW/DENY + reason → `M06` audit, and gates command dispatch. |

**Must do**
- RBAC as the floor; ABAC where context matters — time of access, location, data age (§4.5.3).
- Policy as **versioned, deployable configuration**, not imperative code in services (§10.2.3).
- Least privilege on cloud resources — e.g. the twin API gets `Query` on one table, nothing more (§10.2.3).
- Enforce the three decision tiers of §10.5.1: **advisory** / **guarded automation** / **autonomous execution**, per action type.
- Evaluate *guarded automation* constraints against live context before dispatch — the book's worked example blocks irrigation when soil moisture > 80% (§10.5.1).
- Return a machine-readable **reason** on denial; a bare `false` is unauditable.
- Row/field-level masking for visualisation consumers — engineers see raw sensor data, executives see aggregates (§7.6.1).

**JVM notes** — Open Policy Agent as a sidecar, called over HTTP/gRPC from a Spring
`@HttpExchange` client. In-process coarse checks via Spring
Security `AuthorizationManager`; keep *physical-action* policy external so it can be reviewed and
deployed independently of the app.

---

#### M04 — Observability

| | |
|---|---|
| **Responsibility** | Explain system behaviour, not merely detect failure (§10.3.1). |
| **Owns** | Log/metric/trace pipelines, dashboards for twin health. |
| **Consumes** | Instrumentation from every module. |
| **Source** | §10.3.1, §3.6 (automated monitoring), §10.3.4 (cost as a signal). |

**Must do**
- Structured logs, queryable by attribute, with deliberate levels (§10.3.1).
- **A trace identifier that follows one physical event end to end** — sensed → ingested → graph lookup → policy → command dispatched, with latency attributed per hop (§10.3.1 shows a 90 ms hallway-motion→light trace).
- Prioritise *business* metrics over CPU/memory. The book's canonical example: `twin.prediction.drift_celsius` — the delta between predicted and measured — surfaces model degradation before it affects decisions (§10.3.1).
- Sensor-network health: message frequency, battery voltage, missed-transmission alerts (§3.6).
- Expose **cost** as an observable signal, attributable per model/asset/simulation/tenant (§10.3.4 note).

**JVM notes** — Micrometer + Micrometer Tracing + OpenTelemetry (the book names OTel as the
standard, §10.3.1). Spring Boot 4 ships Micrometer 1.16-era autoconfiguration and OTel support.
Carry `trace_id` across virtual threads with `ScopedValue`, not `ThreadLocal` — `ThreadLocal`
plus thousands of virtual threads is a memory-footprint mistake.

---

#### M05 — Configuration & Secrets

| | |
|---|---|
| **Responsibility** | Environment-specific configuration and credential material, versioned and auditable. |
| **Owns** | Config sources, secret references (never secret values in the repo). |
| **Source** | **[not in book]** as a named concern; implied by §10.2.2, §10.3.3, App. A/B credential handling. |

**Must do**
- Per-environment config with the local-first Docker path the book relies on kept working (§4.7.1, §5.4.3, §6.4.3 all run local containers).
- Secrets from a managed store; rotation without redeploy.
- Codec/decoder configuration, sensor calibration coefficients and noise parameters (`R` per sensor, §3.5.3) are configuration, not constants — they change in the field.

**JVM notes** — Spring Boot config trees + `spring-cloud-vault`/cloud secret managers.
Testcontainers `@ServiceConnection` reproduces the book's Docker-compose dev loop in tests.

---

#### M06 — Audit & Command Sourcing

| | |
|---|---|
| **Responsibility** | Make every decision the twin makes or recommends replayable. |
| **Owns** | Append-only decision/command log. |
| **Consumes** | Proposals from `M37`/`M38`/`M40`; verdicts from `M03`. |
| **Produces** | Immutable events; queryable alongside telemetry. |
| **Source** | §10.5.4, §10.5.1, §10.3.3. |

**Must do**
- **Write the command event to an append-only log *before* execution** — command sourcing (§10.5.4).
- Capture the full decision context: input data + timestamps, model version and parameters, rules applied, governance verdict, resulting action (§10.5.4).
- Persist **denied** commands too — the book's worked example is a DENIED irrigation event, and the denial is what reveals the model over-weighted temperature and cloud cover (§10.5.4).
- Use a portable envelope. The book uses CloudEvents (§10.5.4) — see §4 below.
- Support replay: reconstruct why the twin behaved as it did without guesswork.

**JVM notes** — Spring Modulith's Event Publication Registry gives you at-least-once handoff from
"logged" to "dispatched" with recovery, which is exactly the ordering guarantee command sourcing
needs. CloudEvents has a maintained Java SDK. Store in an append-only table or an event log
(Kafka with compaction off).

**Traps** — §10.5.4 warning: without contextual audit trails, teams speculate after incidents,
trust erodes, operators disable automation, and production use gets blocked outright in regulated
environments.

---

#### M07 — Data Governance, Classification & Retention

| | |
|---|---|
| **Responsibility** | Classify data, enforce handling rules, and bound how long and for what purpose it is kept. |
| **Owns** | Classification labels, retention policies, purpose-limitation register. |
| **Source** | §4.5.1, §4.5.2, §10.5.3, §10.5.2. |

**Must do**
- Four-tier classification — public / internal / confidential / restricted — enforced across *all* sources, since the twin aggregates from many (§4.5.1).
- Regulatory hooks: data minimisation, portability, right to deletion, access audit trails (§4.5.2). Sector overlays as needed (HIPAA/SOX/PCI DSS/GDPR).
- **Purpose limitation.** Record why each dataset was collected and where its use must stop (§10.5.3). The twin's defining strength — fusing data never meant to be viewed together — is also its ethical risk; secondary use is the common failure (reliability data repurposed for performance review, safety telemetry for discipline).
- Named owner per layer: physical sources & ingestion, data models & schemas, simulation/analytics logic, ML models, business rules (§10.5.2).

**Build vs buy** — build the register and the enforcement points; buy classification tooling only at enterprise scale.

---

### Layer 1 — Edge & Acquisition (ch. 3, app. A/B)

---

#### M08 — Device & Sensor Registry, Fleet Management

| | |
|---|---|
| **Responsibility** | Know what is deployed, where, measuring what, in what health. |
| **Owns** | Device inventory, firmware state, calibration schedule, battery history, logical↔physical mapping. |
| **Produces** | Device metadata to `M12` (which codec), `M24` (graph nodes), `M04` (health metrics). |

**Must do**
- Maintain **both** the logical location (what the sensor measures) and the physical location, *with photographs of the exact mounting position* (§3.6). Without this, the data stream is meaningless or misleading.
- Battery telemetry per message → predictive replacement, not failure discovery (§3.6).
- Firmware lifecycle: incremental delivery, maintenance windows, **rollback support to avoid bricking remote devices** (§3.6).
- Calibration schedules and recalibration coordination — edge cameras and pH meters drift when moved (§3.6).
- Batch operations over device *groups* (type, location, criticality, deployment date), not one-by-one (§3.6).
- Register device profile / service profile / destination mapping for LPWAN devices (App. A.4–A.5).
- Alert when a sensor misses a scheduled transmission (§3.6).

**JVM notes** — Spring Data JPA over Postgres; this is reference data (§4.1.1), it changes slowly.
For LwM2M device management Eclipse Leshan is the JVM option; Eclipse Hono for large-scale
connectivity. The book notes fleet-management services exist (AWS/Azure) but a home-scale twin
doesn't justify them (§3.6) — **defer the fleet-scale features, not the registry itself**.

**Traps** — the mitigation the book leans on hardest is *standardisation*: limiting manufacturers
and device types collapses management complexity (§3.6). Registry design should make
non-standard models visibly expensive.

---

#### M09 — Edge Runtime / Gateway Agent

| | |
|---|---|
| **Responsibility** | Process, filter and buffer close to the asset; survive disconnection. |
| **Owns** | Local buffer, edge model artifacts, local decision state. |
| **Produces** | Compact, pre-processed messages to `M11`. |
| **Source** | §3.4.1, §1.2.2, §10.3.4 (edge vs cloud), §7.6.3. |

**Must do**
- Run inference locally and transmit results, not raw payloads. The book's water-meter camera does OCR on-device: 100 KB image → 10-byte reading, a **10,000× transmission reduction** (§3.4.1).
- Retain the ability to periodically send the raw artifact so edge processing can be **verified** (§3.4.1) — this is what makes edge AI debuggable.
- Store-and-forward across network outages; the book's own architecture lost data before this existed (§3.7.2 principle 6).
- Autonomous local decisions when connectivity is absent (§3.4.1).
- Protocol bridging where the device can't speak MQTT (§3.3.4 step 1).

**JVM notes** — For gateway-class hardware, a Spring Boot app with AOT class loading (JEP 483/514/515)
and a modest heap is viable; for microcontrollers it is not — that's C/C++ or MicroPython (App. B
uses an ESP32). Draw the JVM boundary at the gateway. Java 25's FFM API (final since 22) lets you
call a native inference runtime without JNI. LiteRT-class models are named in §10.3.4.

**Traps** — §3.4.1 CAUTION is the most under-appreciated warning in the book: the author's
water-meter model misread "6" as "8" ~10% of the time, producing **phantom consumption spikes that
looked like leaks**. Morning shadows, midday glare and condensation each degraded accuracy; the
fix took several weekends of retraining plus fabricating a physical sun shade. **Budget field
tuning time, not lab time.**

---

#### M10 — Protocol Adapters

| | |
|---|---|
| **Responsibility** | Terminate every wire protocol and normalise onto the ingest bus. |
| **Owns** | Per-protocol connection state and translation. |
| **Produces** | Raw payload + metadata onto `M11` topics. |
| **Source** | §3.3.1–§3.3.4, §3.7.1, §4.2.1, App. A. |

**Must do**
- Support a **hybrid** network by design, not as an exception: LoRaWAN via network server, Wi-Fi devices publishing MQTT directly, BLE via a phone-mediated hop, wired OT protocols (§3.3.3, §3.7.1).
- LoRaWAN path: gateway → network server (authenticate device, decrypt, publish to topic) → bus (§3.7.1 steps 4–5).
- Industrial protocols where present: Modbus, OPC UA, EtherNet/IP, Profibus (§4.2.3). *Industrial deferrable for home scope.*
- Preserve metadata through translation — origin, signal quality, gateway, timestamp (§3.3.4 step 4).
- Adding a protocol must not require touching core logic (§3.7.2 principle 3).

**JVM notes** — **Eclipse Milo** for OPC UA (client and server) is the strongest OT story on the
JVM. `j2mod` or Digitalpetri for Modbus. Eclipse Tahu for Sparkplug B. LoRaWAN network server is
*not* something you write — use ChirpStack or a managed one (the book uses AWS IoT Core for
LoRaWAN, App. A.1.2). Each adapter is a Spring Boot module with its own lifecycle.

---

#### M11 — Ingest Bus & Topic Taxonomy

| | |
|---|---|
| **Responsibility** | One decoupled message bus that all acquisition paths converge on. |
| **Owns** | Broker, topic namespace, QoS/retention policy, subscription ACLs. |
| **Source** | §3.3.4, §3.7, §7.6.3. |

**Must do**
- MQTT as the universal transport; publish/subscribe so sensors, applications and analytics services can be added or removed independently (§3.3.4).
- **A hierarchical topic taxonomy mirroring the asset structure**, enabling wildcard subscriptions — `home/water/#` captures all water flow (§3.3.4).
- **Use logical identifiers in topics, never physical device IDs.** The book is emphatic: this lets you swap sensor hardware without renaming topics, and use the same topic whether data arrives via LoRaWAN or Wi-Fi (§3.3.4).
- Every source converges here — networked sensors, edge cameras, and manual entry alike (§3.7).
- Support streaming push to consumers for near-real-time UI (§7.6.3).

**JVM notes** — Spring Integration MQTT (Eclipse Paho underneath) for inbound/outbound adapters;
HiveMQ's Java client if you want MQTT 5 features and back-pressure control. Broker: Mosquitto,
HiveMQ, EMQX, or a managed IoT core. If you also need durable replay and stream processing,
bridge MQTT → Kafka and let `M16` work on Kafka.

**Traps** — this taxonomy is the highest-leverage decision in Layer 1 and the most expensive to
change later. Get the logical-identifier rule right on day one.

---

#### M12 — Payload Codec Service

| | |
|---|---|
| **Responsibility** | Turn device-specific encoded payloads into canonical, unit-bearing, contextualised readings. |
| **Owns** | Codec registry keyed by device type/profile. |
| **Consumes** | Raw payloads from `M11`; device type from `M08`. |
| **Produces** | Canonical telemetry envelope (§4) → `M16`/`M20`. |
| **Source** | §3.5.2, §3.5.1, §3.7.1 step 6. |

**Must do**
- One subscriber across all sensor topics, dispatching to a per-message-type decoder (§3.5.2).
- Bit-level binary decode: Base64 → bytes → signed/unsigned big-endian fields with scaling factors. The book's Dragino LHT52 sends temperature as an integer ×100 to keep the packet small (§3.5.2).
- Three jobs, explicitly: **decode** (binary → values), **standardise** (consistent JSON/record shape across sensor types), **add context** (units and metadata absent from the raw data) (§3.5.2).
- New device type = new codec registration, no redeploy of the dispatcher ideally.
- Reject/quarantine undecodable payloads loudly — the book's early versions produced *silently corrupt readings from misconfigured decoders* (§3.7.2 principle 6).

**JVM notes** — This is where Java is *better* than the book's Python. Model readings as `record`s
and codecs as a `sealed interface`, dispatch with pattern matching for switch. Decode with
`ByteBuffer` (`order(BIG_ENDIAN)`, `getShort()`, `getChar()` for unsigned 16-bit) or
`MethodHandles.byteArrayViewVarHandle` for zero-copy. Register codecs via `ServiceLoader` or
Spring's `BeanRegistrar` for dynamic sets. The book uses serverless compute here for variable
volume and near-zero idle cost (§3.5.2) — on the JVM the equivalent is a small always-on service
with virtual threads, or AOT-optimised functions if you genuinely need scale-to-zero.

---

#### M13 — Manual Data Capture

| | |
|---|---|
| **Responsibility** | Make human-collected measurements a first-class, systematic input. |
| **Owns** | Capture forms, submission validation, collection schedules. |
| **Produces** | Readings onto `M11`, indistinguishable downstream from electronic ones. |
| **Source** | §3.4.2, §3.4.3, §3.7.1 (last para). |

**Must do**
- Accept that some measurements will never be automated economically: automated pool-chemistry sensors cost more than the book's entire twin, a $50 test kit gives comparable accuracy (§3.4.2).
- Mobile-friendly entry **available where the measurement is taken** (§3.4.3).
- Capture **qualitative observations sensors would miss entirely** — water clarity, debris level (§3.4.3). This is the unique value of a human in the loop, not a consolation prize.
- Show immediate value at entry time — alerts or trends — or collection will be abandoned (§3.4.3).
- At scale: validation rules, duplicate detection, automated quality checks, and smart reminders factoring time-since-last-reading and abnormal weather (§3.4.3).
- Support file-based import for devices that only export locally — the book's electricity meter syncs to a phone and requires a CSV export (§3.4.2).

**JVM notes** — Spring MVC/WebFlux endpoint + a thin PWA; publish to the broker server-side rather
than the book's browser-direct-to-broker shortcut (§3.4.3 listing 3.3), which would leak
credentials in a multi-user setting.

**Traps** — the book's honest framing: a provider portal delayed by 48 hours makes real-time
optimisation impossible while leaving strategic decisions viable (§3.4.2). Record data *latency*
per source, and let `M27` expose it.

---

#### M14 — Sensor Fusion & State Estimation

| | |
|---|---|
| **Responsibility** | Produce the best single estimate of true state from multiple imperfect sensors. |
| **Owns** | Per-sensor noise parameters, filter state. |
| **Consumes** | Canonical readings; optionally a physics model from `M42`. |
| **Produces** | Fused state estimates as derived data (§4.1.5). |
| **Source** | §3.5.3, §9.3.4. |

**Must do**
- Fuse multiple sensors of the same quantity; simple averaging is insufficient (§3.5.3).
- Kalman filter with a **per-sensor measurement noise parameter `R`**. The book is blunt: the algorithm is the easy part, knowing how much to trust each sensor is the hard part — get `R` wrong and the filter over-trusts a bad sensor or ignores a good one (§3.5.3).
- Handle **sensor drift**, not just noise. The book's example injects a +2 °C bias into one of five sensors partway through — a common real failure that averaging cannot handle (§3.5.3).
- Optionally run the unscented variant with a physics model in the prediction step, which tracks truth closely despite noisy inputs (§9.3.4). This is the same module as `M42`'s consumer — build the seam.

**JVM notes** — **Hipparchus** (`org.hipparchus.filtering.kalman`) provides linear, extended and
unscented Kalman filters and is the Apache Commons Math successor. EJML for the matrix work if you
hand-roll. This is a clean, well-served JVM capability — no Python needed.

---

### Layer 2 — Data Platform (ch. 4)

---

#### M15 — Canonical Model & Schema Registry

| | |
|---|---|
| **Responsibility** | Define and version the shapes that cross module boundaries. |
| **Owns** | Schemas for telemetry, commands, asset records; compatibility rules. |
| **Source** | §3.3.4 (payload standardisation), §10.3.3, §4.1. |

**Must do**
- Cover the five data types the book distinguishes, because each has different storage and access characteristics: **reference**, **timeseries**, **unstructured/semi-structured**, **spatial**, **derived** (features + embeddings) (§4.1).
- **Additive-only schema evolution wherever possible.** §10.3.3 tip: adding `power_kw` is backward compatible; renaming `temperature` to `temp_c` **silently breaks downstream consumers**.
- Producers and consumers must evolve independently — a new sensor field must not require redeploying every consumer (§10.3.3).
- Version schemas; treat schema change as a governed change (`M45`).

**JVM notes** — Java `record`s as the in-process contract, generated from or validated against
registered schemas (Avro/JSON Schema/Protobuf). Confluent Schema Registry or Apicurio if on Kafka.
Spring Boot 4's JSpecify null-safety annotations make optionality part of the contract rather than
a convention.

---

#### M16 — Streaming Ingest & Windowed Processing

| | |
|---|---|
| **Responsibility** | Continuous ingest with in-stream aggregation and threshold detection. |
| **Owns** | Stream topology, window state. |
| **Produces** | Rolling aggregates → `M20`, `M34`; trigger events → `M36`. |
| **Source** | §4.4.2, §7.6.3, §3.5.1. |

**Must do**
- Continuous ingest keeping the twin a live representation (§4.4.2).
- **Sliding-window aggregates** — mean/min/max over recent data. The book's rationale: a 30-second sliding average smooths noise on a 1 Hz sensor while still responding quickly (§4.4.2).
- Real-time alerts when windowed trends cross thresholds (§4.4.2).
- Handle the "5 Vs" characteristics of IoT data — volume, velocity, variety, veracity, value (§3.5.1).

**JVM notes** — This is a JVM strength. **Kafka Streams** or **Apache Flink** for real windowing
with event-time and watermarks. For lighter needs, **Java 25 Stream Gatherers**
(`Gatherers.windowSliding(n)`, `windowFixed(n)`, final since 24) implement §4.4.2's sliding window
in one line without a stream-processing cluster — genuinely the right tool for a home-to-building
scale twin. Virtual threads make the fan-out of per-sensor windows cheap.

---

#### M17 — Batch Ingest & ETL/ELT

| | |
|---|---|
| **Responsibility** | Scheduled bulk ingest with transformation, for slow-moving and historical data. |
| **Owns** | Job definitions, run history, checkpoints. |
| **Source** | §4.4.1, §4.4.4. |

**Must do**
- Scheduled or on-demand chunked ingest — hourly/daily (§4.4.1).
- Cleanse on ingest: validate against expected ranges, convert units, enrich from reference data (§4.4.4).
- Aggregate once at ingest rather than per dashboard load — the book pre-computes weekly power totals for exactly this reason (§4.4.4).
- Support ELT too: land raw, transform in the target store (§4.4.4 last para).
- Idempotent, restartable, with a record of what was loaded (§4.4.1 reliability rationale).

**JVM notes** — **Spring Batch** is the natural fit (chunked reads, restart, skip/retry policies).
For large-scale transformation, Apache Spark's Java API — the book itself reaches for Spark here
(§4.4.4). `parquet-java` for columnar output; DuckDB's JDBC driver lets you run the book's
"SQL directly over Parquet, no database" pattern (§4.3.2) from Java.

---

#### M18 — External Data Connectors

| | |
|---|---|
| **Responsibility** | Integrate third-party and cross-organisation data the twin doesn't own. |
| **Owns** | Connector configs, credentials refs, response caches, rate-limit state. |
| **Source** | §4.2.4, §4.4.3, §4.2.2. |

**Must do**
- Support all four integration styles the book calls out: **REST** (predictable, cacheable), **GraphQL** (request exactly what you need from rich models), **gRPC** (high-performance, type-safe, schema-generated), **WebSocket** (bidirectional, persistent) (§4.4.3). Plus webhooks for event-driven pull-replacement.
- Enterprise systems: ERP master data (assets, maintenance schedules, procurement, org hierarchy) — the book queries SAP via OData (§4.2.2). Finance/procurement so cost enters operational decisions (§4.2.2).
- Recognise that external systems are sometimes the *only* source for critical data (§4.2.4).
- Concrete feeds the book uses: weather forecast/history (Open Meteo), satellite imagery (NASA GIBS/Copernicus WMS), open transport feeds, utility portals.
- Treat external APIs as versioned dependencies that will break you (§10.3.5 "External API changes") — contract tests + monitoring.

**JVM notes** — Spring Framework 7 **HTTP Service Clients** (`@HttpExchange` interfaces via
`HttpServiceProxyFactory`) give you declarative typed clients in-framework, no Feign needed.
Wrap with `@Retryable` and `@ConcurrencyLimit` (Spring Framework 7 core resilience). Spring for
GraphQL includes a client for GraphQL sources.

---

#### M19 — OT Integration & IT/OT Boundary

*Deferrable for home/building scope. Mandatory for industrial.*

| | |
|---|---|
| **Responsibility** | Get OT data into the twin without letting the twin endanger the OT system. |
| **Owns** | Mirror/replica datasets, DMZ-resident collectors, protocol converters. |
| **Source** | §4.2.1, §4.2.3, §1.2 (OT/PLC/SCADA context). |

**Must do**
- **Never query the process historian or SCADA directly.** Direct queries impact SCADA performance and its interfaces aren't built for analytical workloads (§4.2.1). Deploy collectors that subscribe to SCADA streams, buffer, and forward to a **mirror**; the twin reads the mirror (§4.2.1).
- Respect air-gapped networks, unidirectional gateways and data diodes — these force reliance on replicated rather than live data, and that's a correct constraint, not an obstacle to route around (§4.2.3).
- Implement the industrial DMZ pattern: mirror servers, protocol converters (Modbus→MQTT), security gateways — aligned to the Purdue model layering (§4.2.3).
- Accept the latency and storage overhead of mirroring as the price of protecting operational systems (§4.2.3).
- Distinguish OT from IIoT correctly: OT is real-time control on isolated proprietary networks; IoT is data gathering over public networks (§4.2.1). Don't let an IoT-grade component into a control path.

**Traps** — §4.2.3 cites Stuxnet. A compromised IT device must never be able to directly affect
physical infrastructure. If your architecture diagram has a line from the twin straight into a
PLC, it's wrong.

---

#### M20 — Polyglot Persistence

| | |
|---|---|
| **Responsibility** | Store each data type in a technology suited to its access pattern. |
| **Owns** | Schemas, partitioning, indexing, access-pattern documentation. |
| **Source** | §4.3.1–§4.3.6, §4.6, §4.7, §8.2.4. |

The book's position (§4.3 intro): **no single store satisfies all digital twin requirements**;
modern architectures combine several. And §4.6 warns the counter-cost is management burden — pick
the fewest stores that cover your access patterns.

| Store | Holds | Why | JVM access | Book |
|---|---|---|---|---|
| **Timeseries** | Sensor measurements | Compression via delta encoding, pre-aggregation at multiple granularities, time-based tiering | Spring Data JDBC → TimescaleDB; QuestDB (JVM-native); InfluxDB client | §4.3.3, §4.7.1 |
| **Relational** | Reference/asset master data, config | Integrity, consistency; changes monthly-to-yearly | Spring Data JPA → Postgres | §4.3.1, §4.1.1 |
| **Object** | Images, manuals, floorplans, 3D assets, exports, model artifacts | Any type/size, no schema, cheap, storage classes | AWS SDK v2 / MinIO client | §4.3.5, §4.7.2 |
| **Analytical / lakehouse** | Historical columnar for ML & trends | Reads only needed columns; ACID + schema evolution + time travel via open table formats | `parquet-java`, DuckDB JDBC, Spark, Iceberg Java | §4.3.2, §4.3.5, §8.2.4 |
| **Graph** | Entities + relationships (the twin's model) | Natural traversal, flexible schema evolution — see `M24` | Spring Data Neo4j / Neo4j Java Driver (Bolt) | §4.3.6, §4.7.3 |
| **Vector** | Document/telemetry embeddings | Nearest-neighbour semantic search — see `M39` | Spring AI `VectorStore` → pgvector/Qdrant | §4.3.6, §4.1.5 |
| **Feature** | Curated, versioned ML features | Train/serve consistency, lineage, low-latency serving | Parquet + registry, or Feast/Hopsworks | §4.3.6, §8.2 |

**Must do**
- Document access patterns *before* choosing. The book's DynamoDB choice is explicitly a query-first design: partition key = sensor id, sort key = timestamp, attributes = readings, single-table (§4.7.1). Whatever you pick, that discipline transfers.
- Use **open table formats** for anything you'll keep longer than a single tool (§4.3.5 tip — Iceberg for engine independence, Delta if you're Databricks-aligned).
- Prefer Parquet for the ML handoff: columnar column-pruning, strong compression, schema in the footer preserving types (§8.2.4).

**JVM notes** — Postgres + TimescaleDB + PostGIS + pgvector collapses four of these seven into one
operational surface, which directly answers §4.6's integration-complexity warning. That's the
recommendation for anything below industrial scale.

---

#### M21 — Data Lifecycle & Tiering

| | |
|---|---|
| **Responsibility** | Move data across cost/latency tiers as it ages; delete when policy says so. |
| **Owns** | Tiering rules, retention schedules, archive index. |
| **Source** | §4.3.7, §10.3.4. |

**Must do**
- Three tiers with distinct guarantees (§4.3.7): **hot** (live readings, current config — low latency, premium storage, in-memory caching), **warm** (weeks-to-months, reporting/trends/model training — tolerates slower response, compress and aggregate), **cold** (audit/forensic/deep analysis — minutes-to-hours retrieval acceptable for order-of-magnitude savings).
- Automated transition policies, not manual sweeps (§4.3.7 fig. 4.6).
- Pre-compute and retain multiple **grains** (minute/hour/day/month) so `M27` and `M29` can serve any zoom level cheaply (§6.3.2).
- Honour `M07` retention and deletion obligations.

---

#### M22 — Data Quality

| | |
|---|---|
| **Responsibility** | Detect and flag bad data before it corrupts models and decisions. |
| **Owns** | Validation rules, quality flags, missing-data policy. |
| **Produces** | Quality-annotated readings; quality metrics → `M04`. |
| **Source** | §4.5.4, §8.2.1, §1.5.2, §10.3.2 (sensor drift). |

**Must do**
- Per-sensor-type acceptable ranges, plus **cross-sensor plausibility rules** (§4.5.4). The book's LHT52 periodically reports humidity of 0.4% — impossible, and must not become a learned valid state (§8.2.1).
- Explicit missing-data policy: interpolate, backfill, or **mark as missing** — the book marks a cloud-provider outage gap rather than filling it, so no model learns the gap as real (§8.2.1).
- Flag rather than silently drop; quality is metadata that must reach `M27` and the UI (§6.1.2).
- Detect **sensor drift** as a distinct failure class: a low-cost sensor losing calibration by 0.1 °C/month is neither noise nor fault (§10.3.2).
- Surface data **age** — a perfectly rendered chart on stale data is actively misleading (§6.1.2).

**Traps** — §1.5.2 and §4.5.4: faulty readings drive incorrect automated decisions; corrupted
feeds undermine predictive models. This module is cheap and prevents the most expensive class of
failure.

---

### Layer 3 — Model of Reality (ch. 5, §2.4–2.5)

This layer *is* the twin. Everything else is plumbing or presentation.

---

#### M23 — Ontology & Model Definition

| | |
|---|---|
| **Responsibility** | Formally define the concepts, properties, relationships and rules of your domain. |
| **Owns** | Ontology documents, versions, extension register, validation rules. |
| **Produces** | Schema constraints for `M24`, type system for `M27`. |
| **Source** | §5.3, §5.4.2, §5.6.3. |

**Must do**
- Define the four elements the book specifies (§5.3.1): **concepts/classes** (Machine, Product, Building), **properties/attributes** (serialNumber, operationalStatus, lastMaintenanceDate), **relationships** (Product `processedBy` Machine), **rules/constraints** enabling automated reasoning and validation (a Product cannot be `processedBy` a Machine whose status is offline).
- Adopt / extend / create — pick deliberately, and record the trade-off (§5.3.3). Adopting gives interoperability and tool support at the cost of rigidity; extending gives fit at the cost of maintaining divergence; custom gives perfect fit at the cost of shared vocabulary.
- Use a machine-interpretable formalism. The book uses DTDL (JSON-LD syntax; interfaces, properties, telemetry, relationships) and evaluates RealEstateCore vs Google Digital Buildings, choosing RealEstateCore for a home (§5.3.2, §5.3.3). Also relevant: Brick Schema, Project Haystack, SSN/SOSA for sensors, NGSI-LD.
- **Version the ontology and every local extension**, since extensions are what erode interoperability.

**JVM notes** — genuine JVM strength. **Apache Jena** or **RDF4J** for RDF/OWL, SPARQL, and
**SHACL validation** — SHACL is the practical implementation of §5.3.1's "rules and constraints",
which the book describes but doesn't implement. Titanium JSON-LD for DTDL/JSON-LD parsing. If you
stay on a labelled property graph, express constraints as Neo4j constraints plus service-layer
validation.

**Traps** — §5.3.3 CAUTION, and it is the most important paragraph in the chapter:
*selecting an ontology is the easy part; mapping your existing messy reality onto it is the hard
part.* Real buildings and factories don't conform to any class hierarchy. You will hit assets that
fit no class, relationships the ontology doesn't model, properties with no standard equivalent.
**Every extension is a local decision that erodes the interoperability you adopted the standard
to achieve.** Expect to revisit these decisions as your understanding deepens.

---

#### M24 — Knowledge Graph

| | |
|---|---|
| **Responsibility** | Hold the interconnected model of entities and relationships, and answer traversal queries over it. |
| **Owns** | Nodes, edges, properties; the graph is the twin's source of truth for structure. |
| **Consumes** | Ontology from `M23`; entities from `M08`, `M25`; coordinates from `M26`. |
| **Produces** | Traversal results → `M27`. |
| **Source** | §5.4, §4.7.3, §5.2.1, §1.3.1. |

**Must do**
- Implement the ontology as a labelled property graph — labels on nodes *and* edges, key-value properties on both (§5.4.2). RDF triples are the alternative the book also covers (§5.4.2 sidebar).
- Encode all four context types (§5.2.1): **spatial** (where), **temporal** (when, and the trajectory), **relational** (which valve controls this pipe; what maintenance schedule applies), **physical** (part-of hierarchy: sensor → HVAC unit → room → building).
- Support efficient traversal from an identified starting node — the starting node is the key to performance (§5.4.3).
- Link entities the book explicitly includes: structure, rooms, appliances, sensors, **and documents** (manuals, service records, photographs) — the book had to extend RealEstateCore because it lacked an asset→document relationship (§5.4.4).
- Allow new entity and relationship types to be added **without complex schema migration** (§5.4.4) — this is the main reason to choose a graph over relational here.

**JVM notes** — Neo4j with Spring Data Neo4j; Cypher matches the book's queries verbatim (§5.4.3).
Because Memgraph — the book's choice, picked for Docker portability (§5.4.5) — speaks Bolt, the
same Java driver and Cypher work against it, so you can follow the book's local-dev setup exactly
and swap later. Managed alternatives named in §5.4.5: Amazon Neptune, Azure Digital Twins,
AWS IoT TwinMaker, Neo4j AuraDB.

---

#### M25 — Entity Resolution & Contextualisation

| | |
|---|---|
| **Responsibility** | Discover that differently-identified records across systems refer to the same real-world thing. |
| **Owns** | Match candidates, confidence scores, confirmed links, human-review queue. |
| **Produces** | Semantic relationships → `M24`. |
| **Source** | §5.2.2, §5.1.2. |

**Must do**
- Fuzzy identifier matching — the book uses Levenshtein distance / token-set ratio; `"51 Stratford Drive"` vs `"51 Stratford Dr."` are trivially the same to a human and completely different to a computer (§5.2.2).
- **Avoid the O(n²) trap.** Brute-force comparison of 100,000 items is billions of comparisons; use locality-sensitive hashing or blocking (§5.2.2).
- Treat a confirmed match as **discovering a semantic relationship**, not as data cleaning (§5.2.2) — write it to the graph.
- Human review queue with confidence thresholds, domain-specific tokenisation rules, and provenance on every accepted link.
- Capture tacit knowledge deliberately: structured SME interviews, documentation of operating procedures and failure histories, digitisation of handwritten logbooks (§5.2 last para).

**JVM notes** — Apache Commons Text (`LevenshteinDistance`, `JaroWinklerSimilarity`,
`CosineSimilarity`), or `java-string-similarity`. LSH via a MinHash implementation or Spark MLlib's
`MinHashLSH` for the batch pass. Elasticsearch/OpenSearch fuzzy matching is a pragmatic shortcut
for the candidate-generation stage.

**Traps** — §5.2.2 CAUTION: **this is one of the most time-consuming steps in building a digital
twin.** Identifiers follow no consistent pattern, assets get renamed across system migrations,
abbreviations are domain-specific (does `HX-101` match `Heat Exchanger 101`?). Threshold tuning is
a knife-edge — too low and you miss valid matches, too high and you **create false links that
silently corrupt your model.** Industrial implementations require iterative human review of
thousands of candidates and often ML models trained on SME-labelled examples, from SMEs who are
already overcommitted. **Budget for this as a programme, not a sprint.**

---

#### M26 — Spatial Reference & Geometry

| | |
|---|---|
| **Responsibility** | Position everything correctly in space and convert between reference frames. |
| **Owns** | CRS registry, per-asset local origins and transforms, geometry. |
| **Produces** | Global + local coordinates for `M24`, `M30`, `M31`. |
| **Source** | §2.5.1, §2.5.2, §4.1.4, §7.2.2, §7.3.1. |

**Must do**
- Support the CRS families the book enumerates and, critically, **transform between them** (§2.5.1, §2.5.2) — geographic (EPSG:4326/WGS84), projected, geocentric ECEF (EPSG:4978), and local/engineering frames.
- Maintain sensor coordinates in a **local frame** and transform to global on demand via a composed model matrix `M = T × R × S` — translation from lat/lon, rotation from true north, scale (§7.2.2, §7.3.3). The book's home model uses a −20° rotation.
- Proximity and spatial reasoning: "which sensors are within 5 m of this position" via Euclidean distance in the local frame, extensible to 3D (§4.1.4).
- Georeference models properly — an ungeoreferenced model is an isolated object, disconnected from what it represents (§7.3.1).
- Interoperate through GeoJSON / KML so external layers share the reference frame (§7.3.1).

**JVM notes** — **the strongest JVM advantage in this whole catalogue.** GeoTools + JTS (Java
Topology Suite) + Apache SIS give you referencing, EPSG database, datum shifts and projections at
a quality Python's pyproj matches but doesn't exceed. PostGIS + Hibernate Spatial for persistence
and spatial indexes. Do the matrix composition with a small 4×4 double implementation — mirror
Cesium's `eastNorthUpToFixedFrame` semantics exactly so frontend and backend agree.

**Traps** — §7.2 tip, and it will cost you a day if you miss it: **ensure the model's known
reference point (front door, building centre) is at (0,0) in the exported CAD file.** If the asset
is offset 500 m in the CAD file, it is offset 500 m in the twin, and the rotation maths becomes
miserable.

---

#### M27 — Twin Query & Orchestration API

| | |
|---|---|
| **Responsibility** | Answer relational, context-dependent questions by routing across every store — the single seam between the twin and its consumers. |
| **Owns** | The public schema/contract; no primary data. |
| **Consumes** | `M24`, `M20`, `M18`, `M22`, `M44`. |
| **Produces** | Contextually assembled responses to `M29`–`M34`, `M40`, external clients. |
| **Source** | §5.6.1, §5.6.2, §5.6.3, §6.3.2. |

**Must do**
- Implement the four-step retrieval workflow of §5.6.1 exactly:
  1. **Semantic resolution** — query the graph to identify the referenced entity, understanding that "located within" may mean direct placement *or* a spatial hierarchy.
  2. **Entity discovery** — traverse to find related sensors, equipment, documents, images in the same space.
  3. **Data store routing** — use discovered identifiers to query the right timeseries store, document store or live stream.
  4. **Contextual assembly** — combine retrieved data with spatial and functional context into a structured response.
- Answer questions that need no knowledge of sensor identifiers: *"what is the temperature in all rooms on the second floor?"*, *"energy consumption of all HVAC serving the conference rooms?"* (§5.6.1). The client must not need to know which sensor measures what — that indirection is the whole point (§6.2.1).
- Prefer **GraphQL** over REST/OData here. The book's comparison (§5.6.2 table 5.1): REST needs multiple sequential round trips (`/floors/2/rooms` → per-room `/sensors` → per-sensor `/measurements/latest`); OData's `$expand` gets it to one request but the server dictates depth and shape; GraphQL gets exactly the requested fields across relationships in one round trip.
- Base the schema on the ontology — **the GraphQL type system enforces the ontology at run time** (§5.6.3 note).
- **Perform decimation and aggregation server-side, in this module, not in the frontend** (§6.3.2 note is explicit about this). Support: uniform sampling, **peak-preserving sampling** (retain local min/max per window so spikes aren't smoothed away), and visually-lossless simplification (RDP), plus multi-resolution grain selection by requested time range (§6.3.2).
- Expose data **age** and any applied smoothing/aggregation as part of the response — never silently (§6.1.2).

**JVM notes** — **Spring for GraphQL**. Critically, use **DataLoader** batching: the §5.6.1
workflow fans out from one room to N sensors to N latest-measurements, which is a textbook N+1.
The book's Python example doesn't address this and it will bite you at building scale. Spring
Boot 4's API versioning helps you evolve the contract without breaking `M29`'s dashboards.
Keep a thin REST facade too — Grafana's Infinity datasource and Excel both consume HTTP+JSON more
easily than GraphQL (§6.4.3, §6.2.1).

---

#### M28 — Standards & Interoperability Adapters

*Deferrable until you have a concrete integration partner.*

| | |
|---|---|
| **Responsibility** | Exchange models and data with external systems in industry-standard formats. |
| **Owns** | Format mappings, import/export jobs. |
| **Source** | §5.5, §5.5.4, §2.4.2, §7.3.3. |

**Must do**
- Apply the book's two-question adoption test before implementing any standard (§5.5.3): **(1) does it solve a concrete problem** for you — interoperability with a BIM platform (IFC), data integrity with industrial devices (OPC UA); **(2) is it widely adopted** — industry backing, future viability, ecosystem of tools and experts.
- **IFC / BIM** import: geometry *plus* semantics — a wall knows it's a wall, its thermal properties, structural capacity, and relationships to adjacent spaces (§2.4.2). IFC GUIDs are your element-binding key (§7.3.3).
- **Asset Administration Shell** for Industry 4.0 asset exchange: submodels (Digital Nameplate, Technical Data, Maintenance), unique identification (IRDI/URI), and per-stakeholder access control (§5.5.4). The payoff: an asset carrying its own digital passport lets the twin ingest calibration data with no human typing it in.
- **NGSI-LD** if aligning with FIWARE/Garnet-style stacks (§5.4.2).
- **OPC UA information model** exposure for OT consumers (§5.5.1).

**JVM notes** — IFC on the JVM: IfcOpenShell via bindings, or the BIMserver project; neither is
frictionless — budget for it. Eclipse Milo already gives you the OPC UA information model.
Eclipse BaSyx is the JVM AAS implementation. Eclipse Ditto (§10.1.2, JVM-based) is worth
evaluating as a ready-made device/asset abstraction layer.

**Traps** — §7.3.3: even with IFC GUIDs, mismatched naming conventions, missing identifiers and
inconsistent schemas mean linking external data to the correct model element **often requires
manual reconciliation or bespoke middleware** — the book calls it one of the most persistent
integration obstacles in digital twin workflows.

---

### Layer 4 — Experience (ch. 6–7)

§6.1.2 first: **not every twin needs a UI.** If the twin exists purely for automation — an agent
adjusting cooling valves — a visual interface is unnecessary overhead. Build visualisation only
where a human must intervene, diagnose, or confirm the system's logic.

---

#### M29 — Dashboards & Charting

| | |
|---|---|
| **Responsibility** | Timeseries and KPI visualisation for monitoring and analysis. |
| **Consumes** | `M27`. |
| **Source** | §6.4, §6.2.1, §6.2.2, §6.1.1. |
| **Build vs buy** | **BUY. Emphatically.** |

**Must do**
- Adopt Grafana (or Power BI/Tableau/Plotly Dash) rather than building. §6.4.2 lists what "just drawing a few line charts" actually entails: data transformations, query builders, caching and performance optimisation, authentication and permissions, layout systems, alerting, theming, mobile layout. §6.4 note: Grafana's built-ins cover **80–90%** of visualisation needs; the remaining 10–20% may justify custom work, but only after you've validated the standard approach won't work.
- Wire Grafana to `M27` as a datasource. The book's route: Infinity datasource → GraphQL endpoint → `jq` to flatten nested JSON to tabular → transformation to cast the time field to `Time` type (§6.4.3). **Grafana requires a field literally named `time` of type `Time`** or nothing graphs.
- Design per persona (§6.1.2 table 6.2): **operator** — "is the pump running now?" → at-a-glance alerts, colour-coded status; **engineer** — "why did it fail last Tuesday?" → dense timeseries, raw values, historical comparison; **executive** — "are we improving this quarter?" → summarised KPIs, monthly trends.
- Apply the dashboard principles of §6.2.2: the **5-second rule** for critical information; use visual variables (colour, size, position) only where they carry information; match visualisation to data type (a gauge shows a value in a range but can't show a trend); progressively disclose by frequency-of-need and urgency.
- Support **sparklines** and **bullet graphs** — high information density in minimal space, ideal for dozens of sensors in a table (§6.2.1, §6.2.2).
- Offer a dark theme — operators in dimly lit control rooms for long shifts benefit, and colour-coded alerts stand out (§6.2.2 note).

**JVM notes** — nothing to build in Java beyond the `M27` datasource shape. If you do need custom
charts, µPlot is the book's pick (~50 KB, optimised for large timeseries, §6.2.1); that's frontend
TypeScript, not Java.

---

#### M30 — 2D Spatial & Schematic Views

| | |
|---|---|
| **Responsibility** | Show data in the topological or floorplan context that makes it interpretable. |
| **Consumes** | `M27`, `M26` (coordinates), `M20` (floorplan/diagram assets). |
| **Source** | §6.2.3, §6.2.4, §6.2.5, §6.3.1. |

**Must do**
- **Floorplan overlays.** Georeference sensor readings to the floorplan's (x,y) frame. Placing data on a floorplan instantly answers *where* — "the server room is 23.5 °C" — cutting the cognitive load of interpreting an abstract list (§6.2.4).
- **Schematic / P&ID views.** For complex industrial assets, physical layout matters less than functional connection: the priority is **topology (how components connect), not topography (where they sit)** (§6.2.3). Identifiers in the diagram should be auto-linked to their timeseries sources via `M25`.
- **Mimic / HMI views** reproducing the operator panel, overlaying live sensor values on the process layout — and importantly making them accessible **outside the physical control network** (§6.2.3).
- **Heatmaps** for magnitude across two dimensions. The book's hourly-power heatmap revealed electric hot-water boosting between 22:00 and 05:00 that the author didn't know was happening (§6.2.5) — pattern discovery, not just reporting.
- **Geospatial 2D** with overlaid geolocated imagery — e.g. drone orthomosaics over a basemap, cheap enough to rebuild daily for construction/stockpile/farm progress (§6.2.4).
- Pick the rendering technology by element count (§6.3.1): **SVG** below ~5,000 elements (each individually clickable, but DOM-bound); **Canvas** for thousands of dynamic elements; **WebGL** (Pixi.js) for tens of thousands. Apply viewport culling, interaction debouncing, and overview aggregation (§6.3.1).
- Target 60 fps / ~16.7 ms per frame, and **test on real data, not sample data** (§6.3, §6.1.2).

**JVM notes** — frontend work. Java's job is `M27` returning pre-decimated series and correct
coordinates. Keep the coordinate transform in `M26` server-side so the frontend never
reimplements CRS maths.

---

#### M31 — 3D Visualisation Serving

| | |
|---|---|
| **Responsibility** | Serve and render georeferenced 3D scenes with live data anchored in them. |
| **Consumes** | Assets from `M32`, coordinates from `M26`, data from `M27`. |
| **Source** | §7.1.4, §7.2.1, §7.2.2, §7.3.2, §7.3.3, §7.3.4. |

**Must do**
- Stream, don't bulk-load. A single OBJ blocks until fully downloaded, then costs more time converting text to GPU format (§7.2.1). Use tiled formats: **3D Tiles** (hierarchical spatial tree, LOD streaming, mixed photogrammetry/BIM/point-cloud, per-feature queryable metadata) or **I3S/SLPK** for Esri ecosystems (§7.1.4).
- Place the model at real-world coordinates via the composed transform from `M26` (§7.2.2).
- Anchor sensor readings at their true 3D positions by transforming local → global (§7.3.3).
- **Bind data to building elements, not just to coordinates** — a reading belongs to *that* water tank / valve / HVAC unit (§7.3.3).
- Annotations and interactive markers turn a static model into a decision-support interface (§7.3.2).
- Performance techniques, all four (§7.3.4): **LOD** (a pump at 100 m needs 100 polygons, at 1 m maybe 10,000), **frustum culling**, **backface culling**, **occlusion culling** — up to ~90% rendering-load reduction.
- Workflow discipline (§7.3.4): profile before optimising and determine whether you're CPU- or GPU-bound; progressive loading (lightweight overview first, stream fidelity as the user navigates); cache derived sensor statistics and recompute only on source change, never per frame; **adaptive quality — users tolerate reduced resolution but not input lag.**

**JVM notes** — rendering is CesiumJS/Three.js in the browser. Java serves tilesets (ideally via
CDN/object storage, not through the app), issues signed URLs, and provides the data overlay through
`M27`. Don't proxy tile bytes through Spring.

**Traps** — §7.2.1 "the 3D navigation trap": moving from 2D to 3D introduces a real UX hazard —
manipulating a camera in space disorients users. Stick to established paradigms: **orbit mode**
(object-centric, for inspecting an asset) and **walkthrough mode** (human-centric, WASD, for
architectural review). **Never invent your own control scheme** — users rely on muscle memory from
CAD tools and games. Always provide a prominent **"Reset Camera"** button so they can escape the
void.

---

#### M32 — 3D Asset Pipeline & Reality Capture

| | |
|---|---|
| **Responsibility** | Produce, convert, version and georeference the geometric assets the twin renders and simulates against. |
| **Owns** | Asset registry, conversion job state, capture provenance. |
| **Source** | §7.1, §7.2, §7.4, §2.4.2, App. C. |

**Must do**
- Support the geometry types the book distinguishes, because they trade off differently (§2.4.2, §7.6): **mesh** (balanced performance/fidelity, moderate files, needs modelling expertise), **parametric/CAD** (mathematical relationships and constraints preserve design intent; can be driven by sensor readings and reconfigured for simulation), **point cloud** (millimetre accuracy, x/y/z + colour + intensity + timestamp, no surface representation, storage-heavy), **BIM/IFC** (geometry + semantics).
- Capture reality, because as-built diverges from design (§7.4): **photogrammetry** (accessible, photorealistic textures, processing-intensive, less geometrically precise — App. C), **LiDAR** (millimetre accuracy, expensive equipment, large files), **SLAM** (mobile mapping), **NeRF / Gaussian splatting** (novel-view synthesis), **360° photography** (fast, easy, no true 3D geometry).
- Conversion jobs: CAD/OBJ → glTF → 3D Tiles, with LOD generation; track input→output lineage.
- **USD** where multi-vendor composition matters: layered non-destructive composition of models from Revit, Rhino, SolidWorks into one scene; robotics/physics simulation; synchronous distributed design review (§7.4.7). Note the cost honestly — high-performance GPUs, robust networking, 3D pipeline expertise.
- Store assets in `M20` object storage with the structure the book uses: `3d_models/mesh/`, `3d_models/tiles/`, `documents/floorplans/` (§4.7.2).
- Extract data from legacy sources: OCR on scanned utility bills and receipts, blueprint tracing to generate the mesh, drone imagery for roof/solar condition (§2.3, §2.6).

**JVM notes** — **do not implement tiling or photogrammetry in Java.** Orchestrate external tools:
Cesium ion API, Obj2Tiles, py3dtiles, Meshroom/RealityCapture/OpenDroneMap (App. C). Java's role is
the asset registry, job submission, status tracking and result ingestion. Temporal's Java SDK is a
good fit for these long-running, multi-step, retryable jobs. For OCR, Tess4J or a cloud
document-AI service.

---

#### M33 — Realtime Push & Subscriptions

| | |
|---|---|
| **Responsibility** | Deliver state changes to connected clients with minimal delay. |
| **Consumes** | `M16` stream, `M11` bus. |
| **Source** | §7.6.3, §4.4.3, §6.1.2. |

**Must do**
- Streaming to clients over WebSocket / GraphQL subscriptions / MQTT-over-WebSocket, keeping dashboards synchronised with the physical system (§7.6.3).
- Reduce before pushing — downsample point clouds, aggregate timeseries (§7.6.3).
- Always transmit **data age** alongside values, so the UI can honour §6.1.2's rule that stale data must never look fresh.
- Back-pressure and per-subscription filtering, so one client can't force the server to fan out everything.

**JVM notes** — Spring for GraphQL subscriptions over WebSocket, or Spring WebFlux SSE. Virtual
threads make one-thread-per-subscription viable at moderate scale, which simplifies the code
considerably versus reactive chaining.

---

#### M34 — Notification & Alerting

| | |
|---|---|
| **Responsibility** | Get the right signal to the right person or system without training them to ignore it. |
| **Consumes** | Threshold breaches from `M16`/`M37`, anomalies from `M36`, health from `M04`. |
| **Source** | §8.3.1, §8.3.2, §3.6, §6.2.2. |

**Must do**
- Deduplicate, group and rate-limit. §8.3.2 names the failure directly — **alert fatigue** and the resulting tendency to ignore alerts. The book's z-score example flags only the *first* spike; as the spike is absorbed into the rolling mean and σ widens, subsequent spikes stop alerting.
- Context in the alert: what, where (from `M24`), how unusual (from `M36`), what changed.
- Fleet-health alerts: missed transmissions, low battery, calibration overdue (§3.6).
- Routing, escalation and acknowledgement; alerts that nobody owns are noise.

**JVM notes** — Grafana Alerting covers most of this out of the box (buy, per `M29`'s logic).
Build only the twin-specific enrichment: joining an alert to its graph context before it's sent.

---

### Layer 5 — Intelligence (ch. 8)

§8.1 sets the precondition: **most AI failures in digital twin projects stem from poor data
foundations, not poor algorithms.** Without consistent timestamps, aligned signals, clean
readings and contextual metadata, advanced models produce unreliable results. Layers 1–3 are
prerequisites, not parallel work.

---

#### M35 — ML Data Pipeline & Feature Engineering

| | |
|---|---|
| **Responsibility** | Turn raw multi-source telemetry into a clean, aligned, feature-rich matrix for training and inference. |
| **Owns** | Pipeline definitions, feature definitions, output datasets. |
| **Produces** | Parquet feature matrix → `M36`, `M38`; features → feature store. |
| **Source** | §8.2 (all), §4.3.6. |

**Must do**
- **Clean.** Remove impossible outliers so models don't learn them as valid states. Handle gaps explicitly — interpolate, backfill, or mark missing (§8.2.1).
- **Normalise.** Rescale to a common range (min-max to [0,1]). Without it, a 0–5,000 W power feature dominates an 18–28 °C temperature feature purely by magnitude — models sensitive to scale (neural nets, kNN) will mistake size for importance (§8.2.1).
- **Time-align and resample.** Sources arrive at different frequencies *and different timezones* — the book has minutely power, 5-minutely solar (epoch in UTC+8), hourly weather (UTC), 5- and 10-minutely IoT sensors. Synchronise to a common grid (the coarsest source: hourly). Aggregate correctly **per semantic**: sum watt-hours and cost, **mean** temperature (§8.2.2).
- **Extract features**, three kinds (§8.2.3):
  - *Temporal* — **cyclical encoding.** Feeding hour as 0–23 fails because linear models don't know 23:00 and 00:00 are adjacent. Transform to `sin(2πh/24)`, `cos(2πh/24)` so the day maps onto a circle.
  - *Lagged* — shift previous values forward to capture physical memory. The book's double-brick house has thermal inertia; 1/2/3-hour temperature lags let the model see the trajectory, not just the current value.
  - *Domain-specific* — engineer high-signal physical concepts. Indoor−outdoor temperature delta represents heat flux through the building envelope; providing the subtraction explicitly saves the model from having to discover it.
- **Store as Parquet** — column pruning at load, strong compression, schema and types preserved in the footer (§8.2.4).
- Separate transactional from analytical storage. The book is explicit that the DynamoDB + GraphQL path serves dashboards, while ML needs a data lake or feature store (§8.2 intro).

**JVM notes** — **the widest Python↔Java gap after AutoML.** Options, in order of preference:
1. **Apache Spark (Java API)** — the book already uses Spark for aggregation (§4.4.4); `spark-sql`
   covers resample/join/window cleanly and scales. Heaviest dependency.
2. **Tablesaw** — a genuine Java dataframe with time-series resampling and rolling windows; closest
   thing to pandas on the JVM, right-sized for building scale.
3. **DuckDB via JDBC + SQL** — do the alignment and feature maths in SQL over Parquet. Often the
   simplest correct answer, and matches §4.3.2's pattern.
4. **Java 25 Stream Gatherers** for the lag and rolling-window features — `windowSliding(4)` gives
   you 3-hour lags directly.

Cyclical encoding and min-max normalisation are three lines of Java each; don't import a framework
for them.

---

#### M36 — Anomaly & Fault Detection

| | |
|---|---|
| **Responsibility** | Identify behaviour that is statistically improbable, and distinguish it from outright failure. |
| **Consumes** | `M35` features, `M16` streams. |
| **Produces** | Anomaly events → `M34`, `M06`. |
| **Source** | §8.3 (all). |

**Must do**
- Hold the distinction the book draws: **fault detection** identifies a component that has already failed (sensor stopped reporting, returns zero); **anomaly detection** identifies behaviour that is technically valid but statistically improbable — the "unknown unknowns" that are the earliest warning of degradation (§8.3 intro).
- Escalate in the order the book teaches, and stop as soon as it works:
  1. **Rule-based thresholds** — transparent and deterministic, but *absolute*: `power > 3000W` fires when you use the toaster and kettle together, which is normal at 06:00 and anomalous at 03:00. The rule can't tell. Rule count also becomes an administrative burden, and rules only catch what you thought to configure (§8.3.1).
  2. **Statistical** — z-score against a *rolling* baseline, `z = (x − μ)/σ`, typically |z| > 3 over a 24-hour window. This judges the reading in local context rather than against a global constant. The book's example: 15 µg/m³ PM2.5 is an anomaly normally, but not when a nearby bushfire has pushed the daily baseline to 12 (§8.3.2).
  3. **Unsupervised ML** — for multivariate systems where "normal" is a joint condition. A voltage drop is fine when a fridge compressor is idle and not fine when it's starting. **Isolation forest** isolates sparse points quickly while normal clustered points resist separation (§8.3.3).
- Expect and handle benign anomalies. The book's isolation forest flags three classes: a genuine brownout (high power, low voltage), genuine over-voltage near the 253 V standard limit (possibly solar-export-driven), and **defrost events which are normal operation but rare** — correctly detected as unusual, incorrectly interpreted as a problem (§8.3.3). Anomaly ≠ fault; route accordingly.

**JVM notes** — z-score and rolling statistics: plain Java, or Gatherers. Isolation forest:
**Smile** (`smile.anomaly.IsolationForest`) is the direct scikit-learn equivalent; **Tribuo**
(Oracle) offers anomaly detection with a cleaner Java API and model serialisation. Both are
credible — this is *not* one of the gaps.

---

#### M37 — Rule Engine

| | |
|---|---|
| **Responsibility** | Deterministic, cheap, auditable decisions — the default, not the fallback. |
| **Owns** | Rule definitions, versioned. |
| **Source** | §8.3.1, §8.6, §8.6.3, §8.5.4. |

**Must do**
- Evaluate declarative rules over current state and produce proposals into the same governed path as ML/agent decisions (`M03` → `M06`).
- Stay the preferred option where predictability matters. §8.6.2: for high-stakes decisions affecting safety, equipment or operations, deterministic approaches — rule engines, thresholds, physics models — are preferred for **ease of validation**.
- Be honest about the cost comparison. §8.6.3's worked example: an LLM agent at ~$0.05/decision every 15 minutes is $4.80/day; the equivalent rule (`IF solar < 3000W AND temp < 26°C THEN disable_ac`) runs at the edge or as a function for negligible cost. **If the maximum saving is $2.00/day, the smarter approach loses money.**
- Know when to hand over to `M40`: when rules become brittle. §8.5.4 — as you add variables (EV charging, dynamic pricing, seasonal shifts), nested conditionals like `IF solar > 2000W AND nobody home AND it's Tuesday` become unmaintainable. That's the signal to replace logic with semantic reasoning, not before.

**JVM notes** — Easy Rules or Drools for a real engine; often a `sealed interface Rule` with
records and pattern matching is enough and far more debuggable. Since `M03` already runs OPA, you
can express many operational rules as Rego and get versioning, testing and decision logging free —
worth considering before adding a second engine.

---

#### M38 — Prediction, Model Serving & MLOps

| | |
|---|---|
| **Responsibility** | Train, version, serve and monitor predictive models. |
| **Owns** | Model registry, artifacts, training runs, drift baselines. |
| **Produces** | Forecasts and classifications → `M27`, `M34`, `M40`. |
| **Source** | §8.4, §8.6.1, §10.3.2. |

**Must do**
- **Forecasting** (regression on engineered features). The book predicts next-hour indoor temperature from lags + outdoor temp + solar + cyclical time using gradient-boosted trees, reaching MAE 0.019 (§8.4.1).
- **Timeseries forecasting with covariates** — predicting stochastic quantities like power consumption is harder than smooth ones like temperature, because usage spikes instantly when a kettle turns on while temperature has thermal inertia (§8.4.2).
- **Supervised classification** as a *virtual sensor*. The book classifies security-camera frames to answer "is the garage door open?" — labelled by directory placement, no dedicated sensor required (§8.4.3). This is a high-value pattern: use existing cameras to instrument things you never wired.
- **Registry and versioning.** Every prediction must be attributable to a model version and parameter set (§10.5.4).
- **Drift detection, all three types** (§10.3.2 table 10.4):
  - *Data drift* — input distribution shifts beyond training range (panels get dirty, output distribution moves).
  - *Concept drift* — the input→output relationship itself changes (replacing a gas cooktop with induction alters the time-of-day/energy relationship entirely).
  - *Sensor drift* — the measurement mechanism degrades (0.1 °C/month calibration loss).
- **Manage drift by observing distributions, not individual values**, and by escalating to a human. §10.3.2: a sudden 20% consumption increase should *not* be auto-treated as a fault — surface it, let an operator decide whether it's a problem or a new normal, and if it's the latter, label that period and trigger retraining. **Drift detection is about preserving trust more than accuracy: a twin that can explain why its assumptions no longer hold is more valuable than one that silently keeps making confident, outdated predictions.**
- Model lifecycle is a commitment, not an event (§8.6.1) — retraining, redeployment, monitoring. If the team can't support that operational complexity, `M37` is the correct answer even at lower accuracy.

**JVM notes** — split training from serving:
- **Serving in Java: solved.** ONNX Runtime Java, or **JPMML** (`jpmml-evaluator`) for scikit-learn
  models exported as PMML. Deep Java Library (DJL) runs PyTorch/TensorFlow/ONNX engines from Java.
- **Training in Java: partially.** Smile and Tribuo both do gradient boosting, regression and
  classification competently. XGBoost4J gives you real XGBoost.
- **AutoML in Java: not available** — see §5. The book's §8.4.2 (AutoGluon `TimeSeriesPredictor`)
  and §8.4.3 (`MultiModalPredictor`) have no JVM equivalent worth pretending about.
- Drift monitoring: the book uses **Evidently** (Python, §10.3.2) which emits HTML plus
  **structured JSON designed for automated monitoring** — run it as a scheduled Python job and
  ingest the JSON into `M04`. Or compute your own distribution distances (KS statistic,
  population stability index) in Java, which is straightforward.
- MLOps platforms named in §8.6.1: SageMaker, Azure ML, Vertex AI, Hopsworks. Don't build one.

---

#### M39 — RAG & Knowledge Retrieval

| | |
|---|---|
| **Responsibility** | Ground LLM responses in the twin's own documents and data. |
| **Owns** | Document corpus, chunks, embeddings, retrieval config. |
| **Consumes** | `M20` object store, `M24` graph context. |
| **Produces** | Grounded answers and retrieved context → `M40`, UI. |
| **Source** | §8.5.1, §5.2.3, §4.1.3. |

**Must do**
- Ingest the unstructured corpus the twin already holds: appliance/equipment manuals, service receipts, maintenance records, engineering documents, inspection photos (§4.1.3, §8.5.1).
- Chunk → embed → store in a vector store → retrieve → augment the prompt (§8.5.1). Persist embeddings durably; the book's in-RAM example is explicitly a simplification.
- Deliver the two things context buys you (§5.2.3): **grounding** — preventing hallucination by anchoring answers in real asset data, so "what's the optimal pressure for Pump A-7?" gets a real answer instead of a plausible one; **specialisation** — turning a general model into a domain expert over proprietary engineering specs, sensor history and tacit knowledge, enabling chained analysis (read vibration → cross-reference maintenance schedule and operating hours → predict bearing failure).
- The retrieval should not require the user to know identifiers — the book's example asks about "my air conditioner" without naming make or model, because the corpus supplies it (§8.5.1).
- Combine vector retrieval with **graph** retrieval. `M24` already knows which manual belongs to which appliance in which room; that's a better filter than embedding similarity alone. **[not in book — the book keeps these separate; combining them is a straightforward improvement]**

**JVM notes** — **Spring AI** is the natural choice on Spring Boot 4: `VectorStore` abstraction,
document readers/transformers for the ingest ETL, and `QuestionAnswerAdvisor` for the RAG
augmentation step. **LangChain4j** is the equally-capable alternative and is closer in shape to the
book's LangChain code. pgvector keeps you on one database.

**Traps** — §8.5 warning: hosted GenAI services can expose proprietary data to the provider. Use
Amazon Bedrock / Azure AI Foundry so data doesn't leave your VPC. §8.5.3 note makes the industrial
stakes concrete: *imagine the temperature profile of my bedroom is actually critical performance
data of your most important industrial plant.* The book uses Claude via Bedrock precisely so
confidential data isn't sent to the model vendor.

---

#### M40 — Agent Runtime, Tool Registry & Actuation

| | |
|---|---|
| **Responsibility** | Let the twin perceive, reason, plan and act — under governance. |
| **Owns** | Agent definitions, system prompts, tool registry, conversation/loop state, cost budgets. |
| **Consumes** | `M27` (state), `M18` (forecasts), `M38` (predictions), `M39` (documents). |
| **Produces** | Proposed commands → `M03` → `M06` → actuators. |
| **Source** | §8.5.2–§8.5.5, §8.6.2, §10.5.1, §1.3.5. |

**Must do**
- Run the agent loop: reasoning, tool execution, context management across multiple steps — this is what distinguishes an agent from a prompt/response interaction (§8.5.3).
- **Tools with thorough documentation.** The book stresses that method documentation is what tells the agent how to use a tool, including input and return structure (§8.5.3). The home agent's tools: current solar generation (local inverter API), weather forecast (temperature/cloud/radiation for 24 h), indoor climate and consumption (via `M27`), and the actuation tool.
- **System prompt carries role, constants, control policy, safety and priorities** (§8.5.3): target range, load figures, baseload assumptions, the evaluation state machine, **hysteresis** (don't cycle the AC if state changed < 15 min ago), and an explicit priority ordering ("always prioritise minimising grid consumption over comfort").
- **Prefer the supervisory controller pattern.** §8.5.4: the agent should not directly control hardware. It manages **setpoints** of lower-level deterministic controllers — "solar is 3 kW, external temp will rise 8 °C in 3 hours before cloud cover hits 90%, so change the AC setpoint from 22 °C to 20 °C now to pre-cool". The agent does multi-variable optimisation; the deterministic controller does control.
- **Decompose into a multi-agent system** rather than building one "God Agent" (§8.5.5). Single-agent failure modes: context-window exhaustion from every subsystem's prompts and tool definitions (cost + latency), **conflicting goals** (maximise security = keep everything closed; passive cooling = open windows → reasoning loops), and **tool hallucination** rising with tool count. Use narrow specialists plus a supervisor/router.
- Enforce **decision tiers** (§10.5.1) via `M03`: advisory / guarded automation / autonomous execution, documented and enforced in code. Human-in-the-loop mandatory where safety, compliance or significant financial exposure is involved (§8.6.2) — the twin proposes with supporting context, a human approves before any command reaches the physical system.
- **Budget and meter tokens.** §8.5.5 warning: the book passes verbose GraphQL JSON to the model; compressing to CSV would cut cost materially. Track cost per decision and enforce a ceiling (`M46`).
- Treat every agent as a distinct identity with narrow permissions (`M02`, §10.2.2).

**JVM notes** — **Spring AI** provides the agent loop, `@Tool`-annotated method registration
(directly analogous to the book's Strands `@tool` decorator), chat memory, and MCP client/server
support for exposing tools across process boundaries. LangChain4j is the alternative. Point the
model provider at Bedrock/Azure so §8.5's data-residency warning is satisfied by configuration.

**Traps** — §8.5.4: LLM agents are **non-deterministic**; the same input can produce different
output because they're statistical models. They hallucinate and can confidently emit incorrect or
unsafe instructions. Guardrails are not optional — which is exactly why `M03` sits between this
module and any actuator, and `M06` records the attempt whether or not it was allowed.

---

### Layer 6 — Simulation (ch. 9)

§9.1.1 states the distinction that matters: traditional simulations use fixed parameters and drift
from reality; **a digital twin continuously updates its inputs from live data and compares
predicted against observed outputs to calibrate itself.** That closed feedback loop is what makes
simulation operational rather than retrospective.

---

#### M41 — Simulation Orchestration & Scenario Management

| | |
|---|---|
| **Responsibility** | Define, execute, track and store simulation runs and what-if scenarios. |
| **Owns** | Scenario definitions, run registry, results, parameter provenance. |
| **Consumes** | Live state from `M27` for initialisation. |
| **Source** | §9.1.2, §9.1.1, §9.4.1. |

**Must do**
- Run models forward in time under specified inputs, constraints and disturbances (§9.1.2).
- **Initialise from live state**, not defaults — grounding simulations in current conditions, degradation and usage patterns is the whole point (§9.1.1).
- Answer what-if questions and store the answers with their inputs: "how would throughput change with an extra line?", "where are the bottlenecks?", "what if delivery times rise 20%?" (§9.4.1).
- Support **many rapid runs** — thousands of scenarios, and Monte Carlo over stochastic inputs to produce ranges of possible futures rather than a single deterministic prediction (§9.4.1).
- Record model version, parameters and inputs per run so results are reproducible and auditable (`M06`, §10.5.4).

**JVM notes** — Spring Batch for parameter sweeps; Temporal (Java SDK) if runs are long, multi-step
and must survive restarts. Results to `M20`'s analytical store as Parquet.

---

#### M42 — Continuous Simulation & FMI Co-simulation

| | |
|---|---|
| **Responsibility** | Simulate smoothly-evolving physical state — thermal, fluid, electrical. |
| **Source** | §9.2, §9.3, §9.3.4. |

**Must do**
- Solve differential equations describing rates of change; treat it as an initial value problem with a numerical integrator (§9.2, §9.2.2).
- Support **acausal modelling** — declare physical equations and component connections and let the tool derive the causality, rather than hand-writing the solve order (§9.3). The book models tank drainage in OpenModelica.
- **Execute exported models rather than reimplementing them.** OpenModelica → **FMU** (Functional Mock-up Unit) → execute from your runtime (§9.3.3). This is the integration point that matters: domain engineers author in their tools, the platform executes the artifact.
- **Fuse with live sensor data.** §9.3.4's pattern is the one to build: an unscented Kalman filter uses the FMU in its *predict* step and the noisy IoT readings in its *update* step, producing an estimate that tracks true state closely despite sensor noise. Models drift from reality due to unmeasured disturbances; sensors suffer noise, bias and coverage gaps — the filter compensates for both.

**JVM notes** — **FMI4j** or **JavaFMI** to load and step FMUs from Java; this is a real, working
JVM capability and the cleanest bridge to the Modelica ecosystem. Hipparchus ODE integrators
(`DormandPrince853Integrator`, `AdamsBashforthIntegrator`) for models you write yourself, and
`UnscentedKalmanFilter` for the §9.3.4 estimator — same library as `M14`, so build them together.

---

#### M43 — Discrete Event Simulation

*Deferrable unless you model processes, queues or logistics.*

| | |
|---|---|
| **Responsibility** | Simulate systems that change at events rather than continuously. |
| **Source** | §9.4, §9.1.3. |

**Must do**
- Advance an event calendar rather than fixed time steps; each event mutates state and may schedule future events (§9.4 intro).
- Model actors progressing through the system — machines, jobs, vehicles, customers — with resources, queues and capacities (§9.4.2).
- Incorporate **stochastic variability** via probability distributions on event timing and outcomes (§9.4.1).
- Natural domains per §9.4.1: manufacturing, supply chains, hospitals, transportation networks.

**JVM notes** — **DESMO-J** is the mature Java DES framework and the closest analogue to the book's
SimPy. Worth noting: SimPy's generator-based process model — which is what makes it pleasant —
maps unusually well onto **Java 25 virtual threads**, where a "process" is just a virtual thread
blocking on a simulated-time barrier. Rolling your own on that basis is more viable in Java 25
than it was in Java 17. Structured concurrency (`StructuredTaskScope`) would help, but it is still
**preview in Java 25** — don't make it load-bearing.

---

#### M44 — Reduced-Order Models, Virtual Sensing & Calibration

| | |
|---|---|
| **Responsibility** | Deliver physics-grade estimates in real time, where no sensor exists. |
| **Produces** | Virtual sensor readings → `M27`, `M20` (as derived data). |
| **Source** | §9.5.2, §9.5.3, §9.7, §9.6, §9.1.1. |

**Must do**
- **Virtual sensing.** Reconstruct state at locations that cannot be physically instrumented — extreme temperatures, geometric constraints, structural interference. The book's canonical case: you cannot put a sensor inside a casting mould filled with molten steel, yet core temperature is vital for quality; feed edge-mounted physical sensors into an FEM solve and reconstruct the full thermal field (§9.5.2).
- **Accept that full-fidelity solvers cannot run in the loop.** §9.5.3: a high-fidelity FEM analysis of a complex asset takes hours or days — useless for a twin reacting to live data. **Reduced-order models are the answer**: mathematically reduce complexity while preserving accuracy. RB-FEA achieves up to ~10,000× speedup, enabling structural condition monitoring at operational speed (the Shell Bonga FPSO, >300,000 tons, is twinned this way).
- Run heavy FEM/CFD **offline** to generate the ROM; evaluate the ROM online (§9.7.1).
- **Data assimilation / calibration.** Compare predicted against observed, adjust parameters, progressively improve projection accuracy (§9.1.1). This closes the loop and is what separates a twin from a simulator.

**JVM notes** — **do not implement FEM or CFD solvers in Java.** Orchestrate external solvers
(OpenFOAM, Elmer, FEniCS, or commercial) as jobs via `M41`; ingest results. ROM *evaluation*,
however, is matrix arithmetic and belongs in Java: EJML or Hipparchus, and the incubating Vector
API (JEP 508, **still incubator in 25**) if you need SIMD throughput. The book's own home-scale CFD
resorts to JAX (§9.6.1) — for the JVM, either precompute a ROM or call out.

---

### Layer 7 — Operations & Value (ch. 10)

---

#### M45 — Change Management

| | |
|---|---|
| **Responsibility** | Change the twin without breaking the physical world. |
| **Source** | §10.3.3, §10.1. |

**Must do**
- Govern change across the four dimensions the book names (§10.3.3): **model** (physics, simulation, ML), **schema** (telemetry formats, asset hierarchies, ontologies), **behavioural** (rules, thresholds, agent logic), **deployment** (edge devices, gateways, cloud services).
- Enforce **decoupling** so change doesn't cascade: telemetry ingestion, state storage, analytics and actuation must be loosely coupled — adding a sensor field must not require redeploying every downstream consumer (§10.3.3).
- **Additive schema change by default** (§10.3.3 tip; see `M15`).
- **Progressive rollout**: shadow deployments, canary releases, A/B — run new logic or models alongside existing ones, generating insight **without influencing the physical system** (§10.3.3). For high-risk changes, route through human-in-the-loop approval before any real-world action.
- Deployment topology per `M46`/§10.1: managed compute, container runtimes, or serverless per component; the book runs managed services at levels 2–3 and a graph database on a VM at level 1 — mixed levels are normal, not a smell.

---

#### M46 — Cost Management & Attribution

| | |
|---|---|
| **Responsibility** | Apply the minimum compute, storage and intelligence that delivers the value. |
| **Source** | §10.3.4, §8.6.3, §4.3.7. |

**Must do**
- Pull the three levers §10.3.4 identifies: **inference frequency** (match to decision cadence; many optimisations are slow-moving and hourly suffices; prefer event-driven over fixed-frequency loops), **edge vs cloud execution** (edge inference eliminates high-volume transfer cost; send aggregates), **data lifecycle and tiering** (`M21`).
- **Attribute cost per model, asset, simulation and tenant** via tagging, per-model metrics and budget alarms — reason about cost with the same rigour as latency or accuracy (§10.3.4 note).
- Trigger a cost review on every new model, sensor or simulation, alongside performance and accuracy (§10.3.4).
- **Model inference frequency before choosing a tool.** §8.6.3: high-frequency loops (sub-second or minute-by-minute) generally rule out LLMs on cost unless you self-host a smaller quantised model. Start with the cheapest method that solves the core problem; upgrade only when the gain justifies the operating cost.

---

#### M47 — Dependency & Supply Chain Management

| | |
|---|---|
| **Responsibility** | Keep the composite system from breaking because something you didn't write changed. |
| **Source** | §10.3.5. |

**Must do**
- **Pin dependencies to exact versions.** Never floating (`^1.2.3`, `latest`) — a malicious published version shouldn't enter your build until you choose it (§10.3.5).
- **Private package repository / proxy** (Artifactory, Nexus, GitHub Packages) rather than pulling from the public internet on every build. §10.3.5's note: when `faker.js` — 20M downloads/month — was removed by its author in January 2022, builds broke worldwide. Cached private copies protect you when a critical dependency disappears.
- **SBOM** per artifact: every library, version and licence (§10.3.5).
- Automated CVE scanning with auto-raised PRs — Dependabot or Renovate — making this routine maintenance rather than crisis work (§10.3.5).
- Track **runtime deprecation** proactively. The book's own example: its LoRaWAN ingestion function runs Node 22.x, deprecated 30 April 2027; ignore the notice and the function eventually can't be updated, risking all downstream functionality (§10.3.5).
- Treat **external APIs as dependencies** — versioning, contract tests, monitoring to catch breaking interface and schema changes before production does (§10.3.5).

**JVM notes** — Maven Enforcer to ban ranges and duplicate classes; `versions-maven-plugin` for
upgrade reporting; CycloneDX Maven plugin for SBOM; OWASP Dependency-Check or Grype for CVEs.
Java's advantage here is real: pinning is idiomatic and the transitive tree is inspectable.

---

#### M48 — Value Measurement & Adoption Analytics

| | |
|---|---|
| **Responsibility** | Prove the twin still earns its place. |
| **Source** | §10.4 (all), §2.1.2, §1.5.1. |

**Must do**
- Baseline before building. §2.1.2 requires measurable KPIs and baselines; §1.5.1 requires a clear definition of success. Without a baseline you cannot demonstrate improvement later.
- Ask the alignment questions on a schedule (§10.4): *What decisions is the twin influencing today? Who or what is making them — operator, automated rule, optimisation agent, hybrid HITL? Is the information still timely and sufficient?* The answers should reshape scope, models, pipelines and level of autonomy.
- **Operational impact** (§10.4.1): reliability (reduction in unplanned outages, MTBF), efficiency (energy per unit output, throughput, waste), responsiveness (decision latency, adaptation speed, recovery time).
- **Adoption and trust** (§10.4.2, table 10.5), four categories — and note the last one:
  - *Adoption (breadth)* — % of intended users active, e.g. shift supervisors logging in during their shift, not just IT and management.
  - *Engagement (depth)* — passive dashboard views vs active analytical use; count predictive-feature usage against view counts.
  - *Influence (action)* — % of twin recommendations approved and acted upon.
  - *Trust (sentiment)* — **the rate at which users disable or ignore automated logic.** If trust drops, usage follows.
- **Financial value** (§10.4.3): `Vt = (C_avoided + R_new + E_savings) − (C_build + C_maintain)`. Cost avoidance (replacing an $80 bearing before the $1,000 motor assembly fails), new revenue impossible without the twin (optimising feed-in under dynamic pricing), efficiency savings (the most common form), against implementation cost (engineering labour usually dominates) and operating cost. §10.4.3 note: `C_build` will always dominate early — the goal is for value to outweigh cost **over time**.

---

## 4. Cross-cutting contracts

Three shapes every module touches. Get these right before writing module internals.

### 4.1 Canonical telemetry envelope

Post-`M12`, everything downstream sees this — regardless of whether the reading came from
LoRaWAN, Wi-Fi MQTT, an OPC UA tag, a CSV import, or a human with a test kit (§3.3.4, §3.5.2,
§3.7.1 last para).

```java
/// One decoded measurement, self-describing and provenance-carrying.
public record Reading(
        String        logicalId,      // logical sensor id — never the hardware id (§3.3.4)
        String        measurement,    // "temperature", "humidity", "power", "ph"
        double        value,
        Unit          unit,           // units are NOT optional (§3.5.2 "context addition")
        Instant       observedAt,     // when the physical world was measured
        Instant       ingestedAt,     // when we learned about it — data age (§6.1.2)
        Quality       quality,        // OK | OUT_OF_RANGE | INTERPOLATED | MISSING | STALE (M22)
        Provenance    provenance) {   // device id, gateway, codec version, transport, rssi/snr
}

public sealed interface Source
        permits Source.Device, Source.Manual, Source.External, Source.Derived, Source.Virtual {
    record Device(String deviceEui, String transport)      implements Source {}
    record Manual(String operatorId, String note)          implements Source {} // §3.4.3 qualitative
    record External(String connectorId, URI endpoint)      implements Source {}
    record Derived(String pipelineId, int version)          implements Source {} // §4.1.5 features
    record Virtual(String modelId, String modelVersion)     implements Source {} // §9.5.2 virtual sensing
}
```

Three deliberate choices:
- **`logicalId`, not device id.** Swap hardware, keep every downstream consumer working (§3.3.4, §6.2.1).
- **Both timestamps.** `ingestedAt − observedAt` *is* data age, and §6.1.2 requires it be visible.
- **`Virtual` is a `Source`.** A reading reconstructed by `M44` must be indistinguishable in
  handling but distinguishable in provenance. **[not in book — the book keeps virtual sensing in
  ch. 9 and never unifies it with the telemetry model; unifying it means one storage path, one
  query path, and honest labelling in the UI.]**

### 4.2 MQTT topic taxonomy

Four levels, mirroring the physical structure so wildcards are useful (§3.3.4):

```
<root>/<domain>/<asset-or-space>/<logical-sensor-id>

home/electricity/electricitymeter/power_meter_1
home/electricity/washingmachine/power_meter_2
home/water/washingmachine/flow_meter_1
home/water/garden/flow_meter_2
home/environment/bedroom1/temp_sensor_1
home/environment/outdoors/temp_sensor_3
```

`home/water/#` → all water flow. `home/environment/+/temp_sensor_1` → that sensor in any space.

The rule that pays for itself: **logical identifiers in the topic, not physical ones.** Hardware
swaps need no topic rename, and the topic is identical whether data arrives over LoRaWAN or Wi-Fi
(§3.3.4).

### 4.3 Command event (the audit primitive)

Written to `M06`'s append-only log **before** execution, allowed or denied (§10.5.4). CloudEvents
envelope for cross-platform portability.

```java
public record CommandEvent(
        String    specversion,   // "1.0"
        UUID      id,
        String    type,          // "com.acme.twin.command.irrigation"
        URI       source,        // "/digital-twin/models/soil-v2" — which model decided
        Instant   time,
        Payload   data) {

    public record Payload(
            Command              command,     // action + target + parameters
            Map<String, Object>  context,     // the sensor state the decision was made on
            Governance           governance) {} // verdict + reason + policy reference

    public record Governance(Verdict status, String reason, String policy) {}
    public enum  Verdict { ALLOWED, DENIED, PENDING_HUMAN_APPROVAL }
}
```

`PENDING_HUMAN_APPROVAL` is the state §8.6.2 and §10.5.1 require and the book's JSON example
doesn't model — guarded automation needs a durable "proposed, awaiting a human" state, not just
allow/deny. **[not in book]**

Why the `context` map matters: §10.5.4's worked example is a **DENIED** irrigation command, and it
is the captured context that reveals the model over-weighted temperature, low humidity and cloud
cover. Denials are your best debugging signal.

---

## 5. JVM library selection

| Capability | Book's tool | JVM choice | Confidence |
|---|---|---|---|
| MQTT client | paho (Python) | Spring Integration MQTT / Eclipse Paho / HiveMQ client | Direct |
| MQTT broker | AWS IoT Core | Mosquitto, HiveMQ, EMQX, managed | Direct |
| OPC UA | — (named §4.2.3) | **Eclipse Milo** | Direct, strong |
| Modbus | — | j2mod, Digitalpetri Modbus | Direct |
| Sparkplug B | — | Eclipse Tahu | Direct |
| Device management | AWS/Azure IoT | Eclipse Leshan (LwM2M), Eclipse Hono, Eclipse Ditto | Direct |
| Binary payload decode | `struct` | `ByteBuffer`, `VarHandle` byte views, FFM | Better in Java |
| Kalman / UKF | numpy hand-rolled | **Hipparchus** `filtering.kalman` | Better in Java |
| ODE integration | scipy `solve_ivp` | **Hipparchus** ODE integrators | Direct |
| FMU co-simulation | OpenModelica → FMU | **FMI4j**, JavaFMI | Direct |
| Discrete event sim | **SimPy** | **DESMO-J**; or virtual threads + simulated clock | Adequate |
| Linear algebra | numpy | EJML, Hipparchus; Vector API *(incubator in 25)* | Adequate |
| Dataframes / resampling | **pandas** | Spark (Java API), **Tablesaw**, or DuckDB+SQL | **Weaker — see gaps** |
| Parquet | pyarrow | `parquet-java`, Apache Arrow Java | Direct |
| Query Parquet as SQL | DuckDB | **DuckDB JDBC** | Direct |
| Stream windowing | manual deque | **Kafka Streams / Flink**, or Java 25 **Stream Gatherers** | Better in Java |
| Batch ETL | pandas + Spark | **Spring Batch**, Spark | Direct |
| Timeseries store | DynamoDB | TimescaleDB, QuestDB (JVM), InfluxDB | Direct |
| Graph store + query | Memgraph + Cypher | **Neo4j + Spring Data Neo4j** (Bolt → Memgraph too) | Direct |
| RDF / OWL / SHACL | — (named §5.4.2) | **Apache Jena**, RDF4J | Better in Java |
| JSON-LD / DTDL | — | Titanium JSON-LD | Direct |
| CRS transforms | pyproj (implied) | **GeoTools + JTS + Apache SIS** | **Better in Java** |
| Spatial persistence | — | PostGIS + Hibernate Spatial | Direct |
| GraphQL API | graphene + Flask | **Spring for GraphQL** + DataLoader | Better in Java |
| Typed HTTP clients | requests | Spring Framework 7 `@HttpExchange` | Better in Java |
| Fuzzy matching | thefuzz | Apache Commons Text, java-string-similarity | Direct |
| LSH / blocking | — | Spark MLlib `MinHashLSH`, MinHash impls | Adequate |
| Anomaly: isolation forest | scikit-learn | **Smile** `IsolationForest`, **Tribuo** | Direct |
| Gradient boosting | scikit-learn HGB | Smile `GradientTreeBoost`, XGBoost4J | Direct |
| **AutoML** | **AutoGluon, FLAML** | **none credible** | **GAP** |
| Model serving | in-process sklearn | **ONNX Runtime Java**, **JPMML**, DJL | Direct |
| Drift detection | **Evidently** | Python job → JSON → ingest; or KS/PSI in Java | Partial |
| RAG / agents / tools | LangChain, Strands | **Spring AI**, LangChain4j | Direct |
| Vector store | in-RAM demo | Spring AI `VectorStore` → pgvector, Qdrant | Direct |
| Policy engine | **OPA (Rego)**, Cedar | OPA sidecar via HTTP; cedar-java | Direct |
| Rules | Python conditionals | Easy Rules, Drools, or sealed records | Direct |
| Observability | **OpenTelemetry** | Micrometer + Micrometer Tracing + OTel | Direct |
| Audit envelope | **CloudEvents** | CloudEvents Java SDK | Direct |
| Dashboards | **Grafana** | Grafana (buy) | Direct |
| Charting (custom) | µPlot | frontend TS — not Java | n/a |
| 3D rendering | Three.js, CesiumJS | frontend TS — not Java | n/a |
| 3D tiling | Cesium ion, Obj2Tiles | **orchestrate external** | GAP by design |
| **FEM / CFD solvers** | scikit-fem, JAX | **orchestrate external** (OpenFOAM, Elmer, FEniCS) | **GAP by design** |
| Photogrammetry | Meshroom etc. | **orchestrate external** | GAP by design |
| OCR | — (implied §2.3.1) | Tess4J, cloud document AI | Adequate |
| Job orchestration | — | Spring Scheduler, Quartz, Temporal Java SDK | Direct |
| Testing | — | JUnit 5, Testcontainers, WireMock, Awaitility | Direct |

### The three real gaps — stated plainly

**1. AutoML has no Java answer.** §8.4.2 and §8.4.3 depend on AutoGluon's `TimeSeriesPredictor`
and `MultiModalPredictor` doing model selection, feature engineering and hyperparameter tuning
automatically — which is precisely the point, since digital twin builders usually have domain
expertise rather than ML expertise. Smile and Tribuo give you *algorithms*, not *automation*.
**Recommendation: train in Python, serve in Java.** Export to ONNX or PMML; serve with ONNX Runtime
Java or `jpmml-evaluator`. Keep the Python training code in the same repo as a separate,
version-pinned module with its own CI. This is a two-language platform; pretending otherwise costs
you §8.4.2 entirely.

**2. FEM/CFD solvers.** No credible pure-Java solver, and you shouldn't want one — §9.5.3 says
full-fidelity FEM takes hours to days and is useless in the loop anyway. The correct architecture
is already in the book: solve offline, build a **reduced-order model** (`M44`), evaluate the ROM in
Java in real time. Java orchestrates the offline solve and does the online matrix arithmetic.

**3. Dataframe ergonomics.** Tablesaw and Spark are workable but neither matches pandas for the
resample-align-lag work of §8.2. **Recommendation: push it into SQL.** DuckDB over Parquet handles
time-bucketing, joins across differently-sampled sources, and `LAG()` window functions natively,
readably, and fast — and it keeps the transformation logic inspectable rather than buried in
imperative Java loops.

---

## 6. Maven layout

**Start as a modular monolith with enforced boundaries.** §10.3.3's decoupling requirement is about
*change isolation*, not process isolation, and Spring Modulith delivers that inside one deployable.
Distributing 48 modules on day one buys you 48 deployment pipelines and distributed tracing
problems in exchange for scaling you don't need yet. **[not in book — the book is
serverless-function-per-concern by default, which is a different but equally valid answer; it works
because AWS manages the plumbing. On the JVM, per-function deployment costs more.]**

```
digital-twin-platform/                  pom (packaging: pom)
├── platform-bom/                       dependency management, all versions pinned
├── platform-contracts/                 §4 records, sealed interfaces, schemas — no deps
├── platform-core/                      M01 M04 M05 — observability, config, build support
├── platform-security/                  M02 M03 — identity, policy client
├── platform-audit/                     M06 M07 — command sourcing, governance
├── edge/
│   ├── device-registry/                M08
│   ├── protocol-adapters/              M10  (submodule per protocol)
│   ├── ingest-bus/                     M11
│   ├── payload-codec/                  M12  (submodule per vendor family)
│   ├── manual-capture/                 M13
│   └── sensor-fusion/                  M14
├── data/
│   ├── schema-registry/                M15
│   ├── ingest-streaming/               M16
│   ├── ingest-batch/                   M17
│   ├── connectors-external/            M18
│   ├── ot-bridge/                      M19   (industrial only)
│   ├── persistence/                    M20 M21  (submodule per store)
│   └── data-quality/                   M22
├── model/
│   ├── ontology/                       M23
│   ├── knowledge-graph/                M24
│   ├── entity-resolution/              M25
│   ├── spatial/                        M26
│   ├── twin-api/                       M27   ← the seam
│   └── standards-adapters/             M28
├── experience/
│   ├── spatial-2d/                     M30
│   ├── viz-3d-serving/                 M31
│   ├── asset-pipeline/                 M32
│   ├── realtime-push/                  M33
│   └── notification/                   M34
├── intelligence/
│   ├── ml-pipeline/                    M35
│   ├── anomaly-detection/              M36
│   ├── rule-engine/                    M37
│   ├── prediction-serving/             M38
│   ├── rag/                            M39
│   └── agent-runtime/                  M40
├── simulation/
│   ├── orchestration/                  M41
│   ├── continuous-fmi/                 M42
│   ├── discrete-event/                 M43
│   └── rom-virtual-sensing/            M44
├── ops/                                M45 M46 M47 M48  (mostly tooling + config)
├── ml-training/                        Python, version-pinned, own CI  → §5 gap 1
├── frontend/                           TypeScript: Three.js/CesiumJS, µPlot  → M29-M33
└── apps/
    ├── app-ingest/                     deployable: edge/* + data/ingest-*
    ├── app-twin/                       deployable: model/* + experience/*
    ├── app-intelligence/               deployable: intelligence/*
    └── app-simulation/                 deployable: simulation/*
```

Notes:
- `platform-contracts` depends on nothing. If it grows a dependency, something is wrong.
- `M29` has no Maven module — Grafana is configuration plus a `M27` datasource shape (§6.4).
- Four `apps/` deployables, not 48. Split further only on evidence.
- **Natural split candidates when you do split**: `payload-codec` (spiky load, scale-to-zero
  appealing), `agent-runtime` (different cost profile and blast radius, per §8.5.5/§8.6.3),
  `simulation/*` (long-running, CPU-bound, must not share a JVM with request-serving).
- Maven 3.9+ is sufficient; Maven 4 is optional and orthogonal to this layout.