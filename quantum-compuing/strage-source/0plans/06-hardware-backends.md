# Section 6 — Hardware Backends, OpenQASM I/O, and Framework Bridges

Implementation plan for the Strange quantum library (`org.redfx.strange`). This
section adds: (a) OpenQASM 2.0/3.0 export and import, (b) Quil export, (c) a
common `RemoteBackend` base implementing `QuantumExecutionEnvironment`, (d)
provider connectors (IBM, IonQ, AWS Braket, Azure), and (e) a Qiskit/Cirq/
PennyLane bridge.

---

## 1. Overview & Goals

Every real-hardware connector must implement `QuantumExecutionEnvironment` so it
is a drop-in replacement for `SimpleQuantumExecutionEnvironment`. The
interface (`QuantumExecutionEnvironment.java`) is intentionally tiny:

```java
Result runProgram(Program p);                       // synchronous
void   runProgram(Program p, Consumer<Result> r);   // callback
default Complex[][] mmul(Complex[][] a, Complex[][] b);
// commented out: // Future<Result> runProgram(Program p);
```

The hard tension: real hardware is **queued and asynchronous** (submit → poll →
fetch), but `runProgram(Program)` is **synchronous and returns a `Result`**. The
plan reconciles this (Section 5).

A second core problem: a `Result` from the simulator carries a full probability
vector (`Complex[] probability`, `2^n` entries) plus per-qubit probabilities.
Real hardware returns only **measurement shot counts** (bitstring → count). We
cannot recover the full state vector, so hardware `Result` objects are built from
an empirical distribution (Section 5.3).

Design constraints (from the existing codebase and the global minimal-dependency
philosophy):
- **No new heavy dependencies.** Use `java.net.http.HttpClient` (JDK 11+) and a
  tiny hand-rolled JSON layer (or `jakarta.json` only if already on the
  classpath — the cloud stub imported `javax.json`, now commented out). See
  Section 8.
- Match existing style: flat package layout, BSD license header on every file,
  Javadoc `<p>...</p>` blocks, `getCaption()`-style naming.
- Backends live under a new package `org.redfx.strange.backend` and QASM tooling
  under `org.redfx.strange.qasm`. The cloud stub stays in
  `org.redfx.strange.cloud`.

---

## 2. Current Interface & Cloud-Stub Analysis (cited)

### 2.1 `QuantumExecutionEnvironment` (real methods)
- `Result runProgram(Program p)` — contract: deterministic probability vector,
  plus `Result.measureSystem()` for stochastic per-qubit values.
- `void runProgram(Program p, Consumer<Result> result)` — async-friendly hook;
  the cloud stub used this to fire the consumer on `ConnectState.SUCCEEDED`.
- `default Complex[][] mmul(...)` — irrelevant to remote backends but must be
  inherited (it is `default`, so no work).

### 2.2 The cloud stub pattern (to extend)
`cloud/CloudlinkQuantumExecutionEnvironment.java` is fully commented out, but it
encodes the exact lifecycle we will generalize:

1. `serializeProgram(Program)` walks `program.getSteps()` → `serializeStep` →
   `serializeGate`, emitting JSON `{numberQubits, QuantumSteps:[{gates:[{caption,
   group, affectedQubitIndex}]}]}`. **Key insight:** it serializes by
   `gate.getCaption()`, `gate.getGroup()`, and `gate.getAffectedQubitIndex()`
   — i.e. there is precedent for a caption-driven Program-walker. Our QASM
   exporter follows the same walk but maps captions to QASM gate names instead.
2. `doRunProgram` returns a `CompletableFuture<Result>`; `runProgram(Program)`
   blocks on `f.get()`; `runProgram(Program, Consumer)` registers a state
   listener that calls `resultConsumer.accept(result.get())` on success.
   **This is the async→sync reconciliation we reuse** (Section 5).
3. `cloud/ResultConverter.java` parses a JSON `{qubits:[...probabilities...]}`
   into `Qubit[]` and builds `new Result(Qubit[], Complex[])`. Our
   `ShotResultBuilder` is the hardware analogue: counts → probabilities →
   `Result`.

### 2.3 The Program model (what the walker traverses)
- `Program`: `getNumberQubits()`, `getSteps()` (ordered `List<Step>`),
  `getInitialAlphas()`. Initial alphas default to `|0>`; if a backend sees a
  non-default alpha it must error (hardware always starts in `|0>`).
- `Step`: `getGates()` (unmodifiable `List<Gate>`), `getType()`
  (`NORMAL`/`PSEUDO`/`PROBABILITY`). **PSEUDO and PROBABILITY QuantumSteps must be
  skipped** by exporters and submitters — they do not alter the circuit.
- `Gate`: `getCaption()`, `getName()` (FQCN), `getGroup()`
  (`SingleQubit`/`TwoQubit`/`ThreeQubit`), `getAffectedQubitIndexes()`,
  `getMainQubitIndex()`, `getSize()`, `getMatrix()`.
- Index accessors per arity:
  - `SingleQubitGate`: `getMainQubitIndex()` → the one qubit.
  - `TwoQubitGate`: `getMainQubitIndex()` (= `first`, the **control** for Cnot/
    Cz/Cr) and `getSecondQubitIndex()` (= `second`, the **target**).
  - `ThreeQubitGate`: `getMainQubit()`, `getSecondQubit()`, `getThirdQubit()`
    (Toffoli: first/second = controls, third = target).

### 2.4 Caption inventory (exact strings, for the gate-name map)
Verified from gate sources:

| Strange class | `getCaption()` | arity | QASM 2.0 name |
|---|---|---|---|
| `Hadamard` | `H` | 1 | `h` |
| `X` | `X` | 1 | `x` |
| `Y` | `Y` | 1 | `y` |
| `Z` | `Z` | 1 | `z` |
| `Identity` | `I` | 1 | `id` |
| `RotationX` | `RotationX <theta>` | 1 | `rx(theta)` |
| `RotationY` | `RotationY <theta>` | 1 | `ry(theta)` |
| `RotationZ` | `RotationZ <theta>` | 1 | `rz(theta)` |
| `Rotation` | `Rotation of <axis> with angle <theta>` | 1 | `rx/ry/rz(theta)` |
| `Cnot` | `Cnot` | 2 | `cx` |
| `Cz` | `Cz` | 2 | `cz` |
| `Cr` | `Cr<pow>` / `Crth` | 2 | `cp(theta)` (controlled-phase) |
| `Swap` | `S` (note: caption is "S"!) | 2 | `swap` |
| `Toffoli` | `CCnot` | 3 | `ccx` |
| `Measurement` | `M` | 1 | `measure` |
| `Fourier`/`InvFourier` | (QFT block) | n | **decompose first** |
| `Oracle`, `Add*`, `Mul*`, `PermutationGate`, `BlockGate` | matrix/block | n | **decompose / `unitary` / error** |

> **Do NOT dispatch on `getCaption()` alone.** Swap's caption is `"S"` (collides
> with a future S-phase gate), and rotation captions embed the angle. The
> exporter dispatches on **`instanceof` / class identity** (a
> `Map<Class<? extends Gate>, QasmEmitter>`), reading angles from the typed gate
> (`RotationX` exposes `thetav`; add a package-private getter or use reflection-
> free access via a new `getTheta()` — see Section 4 cross-ref). Caption is used
> only for diagnostics/unknown-gate messages.

---

## 3. Architecture & New Files

```
org.redfx.strange.qasm
  GateNameTable.java        // Class<? extends Gate> ↔ QASM/Quil names + arity
  QasmExporter.java         // Program → OpenQASM 2.0 (and 3.0 via flag)
  QasmImporter.java         // OpenQASM 2.0/3.0 → Program
  QasmLexer.java            // tokenizer (shared 2.0/3.0)
  QasmParser.java           // recursive-descent parser → AST
  QasmProgram.java          // AST: header + list of instructions
  QuilExporter.java         // Program → Quil
  StrangeQasmException.java // checked/unchecked export/parse errors

org.redfx.strange.backend
  RemoteBackend.java        // abstract base implements QuantumExecutionEnvironment
  BackendCredentials.java   // token/region/url holder (env-var aware)
  JobHandle.java            // {jobId, status enum, pollUrl}
  ShotResultBuilder.java    // counts (Map<String,Integer>) → Result
  HttpJson.java             // thin HttpClient + minimal JSON helper
  IBMQuantumBackend.java
  IonQBackend.java
  BraketBackend.java
  AzureQuantumBackend.java

org.redfx.strange.bridge
  QiskitBridge.java         // Program ↔ Qiskit (via QASM string interchange)
```

---

## 4. Detailed Work Items

### WI-1 — `GateNameTable` (foundation, do first)
File: `qasm/GateNameTable.java`.

A static registry keyed by gate class, used by every exporter and the importer:

```java
public final class GateNameTable {
    public record Entry(String qasmName, String quilName,
                        int arity, boolean parametric) {}
    private static final Map<Class<? extends Gate>, Entry> FORWARD = ...;
    private static final Map<String, Function<int[], Gate>> QASM_FACTORY = ...;
    // FORWARD.put(Hadamard.class, new Entry("h","H",1,false));
    // QASM_FACTORY.put("h", q -> new Hadamard(q[0]));
    // QASM_FACTORY.put("cx", q -> new Cnot(q[0], q[1]));
    // parametric: stored separately, factory takes (int[] qubits, double[] params)
    public static Entry forGate(Gate g);
    public static Optional<Gate> build(String qasmName, int[] qubits, double[] params);
}
```

Parametric gates need angle access. The cleanest path: add a `getTheta()` getter
to `RotationX/Y/Z` and `Rotation` (and `Cr`'s phase). **This depends on Section 1
(parametric gate unification — `ParametricGate` interface with `getParameter()`).
Cross-reference: if Section 1 lands first, query `getParameter()`; if not, add a
minimal `double getTheta()` here.** Flag this dependency in sequencing.

### WI-2 — OpenQASM 2.0 Exporter (Program-walker)
File: `qasm/QasmExporter.java`. Public API:

```java
public String exportQasm2(Program p);              // returns OpenQASM 2.0 text
public void   exportQasm2(Program p, Writer out);
```

Algorithm (mirrors `serializeProgram`'s walk):
1. Emit header:
   ```
   OPENQASM 2.0;
   include "qelib1.inc";
   qreg q[<numberQubits>];
   creg c[<numberQubits>];   // sized to number of Measurement gates' targets
   ```
2. Reject non-default initial alphas: if any `getInitialAlphas()[i] != 1.0`,
   throw `StrangeQasmException` (QASM circuits start in `|0>`; alpha encodes a
   pre-rotation that has no QASM representation).
3. For each `Step s` in `p.getSteps()`:
   - Skip if `s.getType() != Type.NORMAL` (PSEUDO/PROBABILITY are non-physical).
   - For each `Gate g` in `s.getGates()`, dispatch by class via `GateNameTable`:
     - 1-qubit non-parametric: `h q[i];`
     - parametric: `rx(<theta>) q[i];` — format angle with `Locale.ROOT`,
       `%.10g`, or emit symbolic `pi` multiples when close (configurable).
     - `Cnot` → `cx q[<main>],q[<second>];` (main = control).
     - `Cz` → `cz q[<main>],q[<second>];`
     - `Cr` → `cp(<theta>) q[<main>],q[<second>];`
     - `Swap` → `swap q[<a>],q[<b>];`
     - `Toffoli` → `ccx q[<a>],q[<b>],q[<c>];`
     - `Measurement` → `measure q[i] -> c[i];`
   - **Composite gates** (`Fourier`, `InvFourier`, `Oracle`, `Add*`, `Mul*`,
     `PermutationGate`, `BlockGate`): two strategies, selectable via a flag:
     1. **Decompose** using `Computations.decomposeStep(step, nQubits)` (the same
        call the simulator uses at `SimpleQuantumExecutionEnvironment` line ~94)
        to reduce to primitive gates, then export each. *Preferred* — produces
        portable QASM.
     2. **Custom `gate` definition** emitting the raw unitary as a sub-circuit
        (only feasible if the block decomposes to known gates). If a gate is a
        raw `Oracle(matrix)` with no decomposition, throw with a clear message
        (true arbitrary-unitary export needs Section 4's transpiler/decomposer;
        cross-reference).
4. Index convention: Strange qubit index `i` maps directly to QASM `q[i]`. **Note
   endianness:** Strange's `Result.calculateQubitStatesFromVector` treats bit `i`
   of the basis-state integer as qubit `i` (little-endian within the state
   vector, see the `p1 = j/div` loop). QASM/Qiskit use the convention that
   `q[0]` is the least-significant bit of the measured bitstring. These align, so
   no index reversal on export — **but write a round-trip test to confirm**
   (Section 6).

Determinism: iterate QuantumSteps and gates in their stored order; no reordering.

### WI-3 — OpenQASM 3.0 Exporter
Extend `QasmExporter` with `exportQasm3(Program p)`. Differences from 2.0:
- Header: `OPENQASM 3.0;` and `include "stdgates.inc";` (or none).
- Registers: `qubit[n] q;` and `bit[n] c;` (new declaration syntax).
- Measurement: `c[i] = measure q[i];`.
- Parametric gates same names (`rx`, `cp`, etc.).
- Reuse the same per-gate dispatch via `GateNameTable`; only the header/register/
  measure formatting differs. Implement as a `QasmDialect` enum (`V2`, `V3`)
  passed to one shared walker to avoid duplication.

### WI-4 — OpenQASM Importer (parser → Program builder)
Files: `qasm/QasmLexer.java`, `qasm/QasmParser.java`, `qasm/QasmProgram.java`,
`qasm/QasmImporter.java`.

```java
public Program importQasm(String src);     // auto-detects 2.0 vs 3.0 from header
```

Pipeline:
1. **Lexer** (`QasmLexer`): tokenize into IDENT, NUMBER, `pi`, `(`, `)`, `[`,
   `]`, `,`, `;`, `->`, `=`, keywords (`OPENQASM`, `include`, `qreg`/`qubit`,
   `creg`/`bit`, `gate`, `measure`, `barrier`, `if`). Strip `//` and `/* */`
   comments. Tolerate whitespace/newlines.
2. **Parser** (`QasmParser`, recursive descent): produce a `QasmProgram` AST:
   - version, register declarations (name → size),
   - ordered list of `GateCall(name, double[] params, QubitRef[] operands)`,
     `MeasureStmt(src, dst)`, and (best-effort) user `gate` definitions stored as
     macros to be inlined.
   - **Scope (v1):** support `qelib1.inc`/`stdgates.inc` standard gates
     (`h x y z id s sdg t tdg rx ry rz p cx cy cz cp ch swap ccx cswap`), plus
     `measure`, `barrier` (ignored), and single qreg/creg. Defer: classical
     control (`if`), custom `gate` bodies with parameters, arrays of registers.
     Throw `StrangeQasmException` with line number on unsupported constructs.
3. **Program builder** (`QasmImporter`):
   - Allocate `new Program(totalQubits)` from the qreg size(s). If multiple qregs,
     concatenate into a flat index space (record offsets).
   - **Scheduling into QuantumSteps:** Strange forbids two gates on the same qubit in one
     `Step` (`Step.verifyUnique`). Greedily pack consecutive `GateCall`s into the
     current `Step` until a qubit conflict, then start a new `Step` (a simple
     as-soon-as-possible scheduler). Map each `GateCall` to a Strange gate via
     `GateNameTable.build(name, qubits, params)`. Unknown standard gates with no
     Strange equivalent (`s`, `t`, `sdg`, `tdg`, `cswap`) → either depend on
     Section 1 (new gate classes) or throw with a precise message. Cross-
     reference Section 1 (S/T/Fredkin gates).
   - `measure q[i] -> c[j]` → `new Measurement(i)` in its own/next QuantumStep.
   - Set the result on `program` via the normal `addStep` flow (respects
     `ensureMeasuresafe`).

Note: arbitrary-unitary `gate` definitions and `if` (dynamic circuits) are out
of scope here — they belong to Section 7 (classical control). Flag clearly.

### WI-5 — Quil Exporter
File: `qasm/QuilExporter.java`. `public String exportQuil(Program p);`
Reuses the WI-2 walker with the `quilName` column of `GateNameTable`:
- 1-qubit: `H 0`, `X 0`, `RX(theta) 0`, etc. (Quil uses space-separated operands,
  no register prefix, gate names UPPERCASE).
- `Cnot` → `CNOT 0 1`; `Cz` → `CZ 0 1`; `Swap` → `SWAP 0 1`; `Toffoli` →
  `CCNOT 0 1 2`.
- Measurement: declare a classical region `DECLARE ro BIT[n]` once, then
  `MEASURE 0 ro[0]`.
- Composite gates: decompose (same as WI-2). IonQ "QASM-XT" mentioned in
  `ideas.md` is a thin variant of QASM and can be a later flag.

### WI-6 — `RemoteBackend` base (the lifecycle)
File: `backend/RemoteBackend.java`. Abstract class generalizing the cloud stub's
submit/poll/convert flow.

```java
public abstract class RemoteBackend implements QuantumExecutionEnvironment {

    protected final BackendCredentials creds;
    protected int shots = 1024;
    protected Duration pollInterval = Duration.ofSeconds(2);
    protected Duration timeout = Duration.ofMinutes(30);

    protected RemoteBackend(BackendCredentials creds) { this.creds = creds; }

    // --- template-method lifecycle (subclasses fill the gaps) ---
    /** Transpile + serialize Program to this provider's payload (usually QASM/Quil JSON). */
    protected abstract String buildPayload(Program p);
    /** POST the job, return a handle with jobId + poll URL. */
    protected abstract JobHandle submit(String payload);
    /** GET job status. */
    protected abstract JobHandle poll(JobHandle h);
    /** When COMPLETED, fetch shot histogram: bitstring -> count. */
    protected abstract Map<String,Integer> fetchCounts(JobHandle h);

    // --- shared async→sync reconciliation ---
    protected CompletableFuture<Result> submitAsync(Program p) {
        return CompletableFuture.supplyAsync(() -> {
            JobHandle h = submit(buildPayload(transpile(p)));
            Instant deadline = Instant.now().plus(timeout);
            while (!h.status().isTerminal()) {
                if (Instant.now().isAfter(deadline)) throw new TimeoutException(...);
                sleep(pollInterval);
                h = poll(h);
            }
            if (h.status() != Status.COMPLETED) throw new BackendException(h);
            return ShotResultBuilder.fromCounts(fetchCounts(h),
                                                p.getNumberQubits());
        });
    }

    @Override public Result runProgram(Program p) {           // blocks (like stub's f.get())
        try { return submitAsync(p).get(); }
        catch (Exception e) { throw new BackendException(e); }
    }
    @Override public void runProgram(Program p, Consumer<Result> cb) {
        submitAsync(p).thenAccept(cb);                        // non-blocking (stub's listener)
    }

    /** Optional per-provider transpilation hook; default = identity. */
    protected Program transpile(Program p) { return p; }      // cross-ref Section 4
}
```

`BackendCredentials`: reads token/region/instance from constructor args or env
vars (`IBM_QUANTUM_TOKEN`, `IONQ_API_KEY`, `AWS_*`, `AZURE_QUANTUM_*`). Never log
tokens.

`JobHandle.Status`: `QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED` with
`isTerminal()`.

### WI-7 — `ShotResultBuilder` (counts → Result)
File: `backend/ShotResultBuilder.java`. The hardware analogue of
`cloud/ResultConverter`:
- Input: `Map<String,Integer> counts` (e.g. `"011" -> 540`), `int nQubits`.
- Build `Complex[] probability` of length `2^nQubits`: for each bitstring,
  `amplitude = sqrt(count/totalShots)` placed at the basis index. **Parse the
  bitstring with the same endianness the exporter used** (qubit 0 = LSB; reverse
  the provider's string if needed — IBM returns big-endian `q[n-1]...q[0]`).
- Per-qubit `Qubit[]`: marginal probability of each qubit being `1`
  (mirrors `Result.calculateQubitStatesFromVector`). Build
  `new Result(qubits, probability)`.
- Document the lossy nature: phases are gone; only measurement statistics remain.

### WI-8 — IBM Quantum backend
File: `backend/IBMQuantumBackend.java extends RemoteBackend`.
- **Native gate set:** `{ id, rz, sx, x, cx }` (plus `ecr` on newer devices).
  `transpile(p)` must decompose H, Y, Z, rotations, Swap, Toffoli, Cr to this
  basis. **Cross-reference Section 4 transpiler/decomposer** — until that exists,
  rely on IBM's server-side transpilation by submitting generic QASM and setting
  the transpile/optimization option in the runtime payload.
- **REST shape (Qiskit Runtime):** base `https://api.quantum.ibm.com/...`
  (or the IBM Quantum Platform runtime endpoint). Auth: `Authorization: Bearer
  <token>` header (token from IBM Quantum account). Flow:
  - `submit`: `POST /runtime/jobs` with JSON `{program_id:"sampler", backend:
    "<name>", params:{ circuits:[<qasm3 string>], shots }}`.
  - `poll`: `GET /runtime/jobs/{id}` → `{status:"Queued|Running|Completed|..."}`.
  - `fetchCounts`: `GET /runtime/jobs/{id}/results` → quasi-dist / counts.
- `buildPayload`: use `QasmExporter.exportQasm3` (Runtime accepts QASM3).

### WI-9 — IonQ backend
File: `backend/IonQBackend.java`.
- **Native gates:** `{ gpi, gpi2, ms }` (all-to-all trapped-ion); but the IonQ
  API also accepts a high-level gate JSON (`{gate:"x", target:0}`,
  `{gate:"cnot", control:0, target:1}`, `{gate:"rx", target:0, rotation:θ}`).
  **Easiest path: emit IonQ's native circuit JSON directly** from the Program-
  walker rather than QASM (avoids a QASM round-trip). Add an `IonQJsonExporter`
  (or a method on `QasmExporter`) producing the `circuit:[...]` array.
- **REST shape:** base `https://api.ionq.co/v0.3`. Auth: header
  `Authorization: apiKey <key>`. Flow:
  - `submit`: `POST /jobs` `{target:"qpu.harmony"|"simulator", shots,
    input:{format:"ionq.circuit.v0", qubits:n, circuit:[...]}}` → `{id}`.
  - `poll`: `GET /jobs/{id}` → `{status:"ready|running|completed|failed"}`.
  - `fetchCounts`: `GET /jobs/{id}/results` → histogram `{ "0":0.53, ... }`
    (probabilities keyed by **decimal** basis index → multiply by shots).
- `transpile`: minimal (IonQ accepts the high-level gates); Toffoli/QFT must be
  decomposed (cross-ref Section 4).

### WI-10 — AWS Braket backend
File: `backend/BraketBackend.java`.
- Braket wraps IonQ/Rigetti/OQC/IQM and accepts **OpenQASM 3.0** as the IR
  (Braket "openqasm" action) or its native JSON IR. Use `exportQasm3`.
- **No simple REST**: Braket uses AWS SigV4-signed requests
  (`braket.<region>.amazonaws.com`, actions `CreateQuantumTask`,
  `GetQuantumTask`) and results land in an **S3 bucket**. SigV4 signing without
  the AWS SDK is non-trivial. **Decision:** make the AWS SDK an *optional*
  dependency (Maven `optional` scope / separate module) — this is the one place
  the minimal-dependency rule bends, because re-implementing SigV4 + S3 is
  error-prone. Document that `BraketBackend` requires `software.amazon.awssdk:
  braket` + `:s3` on the classpath; otherwise the class throws at construction
  with a clear message. Flow: `createQuantumTask(deviceArn, qasm3, shots,
  s3Bucket)` → poll `getQuantumTask(arn)` until `COMPLETED` → read result JSON
  from S3 → `measurementCounts` → `ShotResultBuilder`.

### WI-11 — Azure Quantum backend
File: `backend/AzureQuantumBackend.java`.
- Azure Quantum accepts QASM (via providers like IonQ/Quantinuum/Rigetti) or
  provider-specific formats. Use `exportQasm3` (or pass-through to the target
  provider's format).
- **REST shape:** workspace-scoped:
  `https://<region>.quantum.azure.com/.../workspaces/<ws>/jobs/{id}?api-version=
  ...`. Auth: Azure AD bearer token (acquire via MSAL / `az account get-access-
  token` — treat token acquisition as caller-supplied to avoid an MSAL
  dependency). Flow: upload input blob → `PUT /jobs/{id}` with
  `{inputDataUri, target, inputParams:{shots}}` → `GET /jobs/{id}`
  (`status: Waiting|Executing|Succeeded|Failed`) → fetch output blob →
  `histogram` → `ShotResultBuilder`. Blob upload uses a SAS URI (string in,
  no Azure SDK needed if the caller provides storage config). Mark Azure Storage
  SDK optional like Braket.

### WI-12 — Qiskit / Cirq / PennyLane bridge
File: `bridge/QiskitBridge.java`.
- **Pure-JVM constraint:** Strange is Java; Qiskit is Python. We cannot construct
  a real `QuantumCircuit` object in-process. The pragmatic bridge is **QASM
  string interchange** (the universally accepted format):
  - `String toQiskit(Program p)` → returns OpenQASM (2.0 or 3.0) string that
    `QuantumCircuit.from_qasm_str(...)` consumes. (Thin wrapper over
    `QasmExporter`.)
  - `Program fromQiskit(String qasm)` → wraps `QasmImporter`.
- Optionally provide a small Python helper snippet (in docs, not a dependency)
  showing `qiskit.QuantumCircuit.from_qasm_str(strange.exportQasm2(prog))`. Same
  pattern serves Cirq (`cirq.contrib.qasm_import`) and PennyLane
  (`qml.from_qasm`). **Document that the "bridge" is QASM-mediated**, not an
  object-level FFI, to set correct expectations. A true object bridge would need
  GraalPython or a subprocess — out of scope, note as a future option.

---

## 5. Async vs Synchronous Reconciliation (design decision)

The interface keeps `Result runProgram(Program)` synchronous. We honor it exactly
as the cloud stub did:

1. **`runProgram(Program)` blocks.** Internally `submitAsync(p).get()`. The
   submit/poll loop runs on a worker thread; the caller thread blocks until the
   job reaches a terminal state or `timeout` elapses (default 30 min, configurable
   via `setTimeout`). On timeout/failure, throw an **unchecked**
   `BackendException` (the interface declares no checked exceptions, so we cannot
   add `throws`).
2. **`runProgram(Program, Consumer<Result>)` is the async-first path** — fire-
   and-forget; the consumer is invoked on completion, exactly like the stub's
   `ConnectState.SUCCEEDED` listener. Recommended for hardware.
3. **Expose `CompletableFuture<Result> submitAsync(Program)` as a public method on
   `RemoteBackend`** (not on the interface). This resurrects the commented-out
   `// Future<Result> runProgram(Program p);` idea without changing the interface.
   Power users poll the future; the `Consumer` overload covers callbacks.
4. **`Result` semantics for hardware differ from the simulator contract.** The
   interface Javadoc promises a *deterministic probability vector*; hardware gives
   *empirical shot statistics*. Document this divergence on each backend class:
   repeated runs yield statistically-close but not identical vectors. This is
   acceptable and unavoidable; `Result.measureSystem()` still works because we
   populate `probability[]` from counts.

---

## 6. Testing Strategy

All tests JUnit, no network in unit tests.

1. **QASM round-trip (export → import → compare):** for a corpus of hand-built
   `Program`s (Bell pair, GHZ, QFT-decomposed, rotation chain, Toffoli), assert
   `importQasm(exportQasm2(p))` reproduces an equivalent Program. Compare by
   running both through `SimpleQuantumExecutionEnvironment` and asserting the
   probability vectors match within epsilon (use the existing simulator as the
   oracle — this also validates index/endianness alignment from WI-2 QuantumStep 4).
2. **Golden-file QASM:** assert exact string output for a few canonical circuits
   (Bell, GHZ) for both 2.0 and 3.0, and for Quil. Keep goldens in
   `src/test/resources/qasm/`.
3. **Importer fixtures:** parse real QASM samples from Qiskit's test suite (Bell,
   adder); assert structural correctness and simulator-equivalence.
4. **Unsupported-construct errors:** assert `StrangeQasmException` with line
   number for `if`, unknown gates, non-default initial alphas.
5. **`ShotResultBuilder` unit tests:** feed synthetic count maps, assert
   probability vector and per-qubit marginals; explicitly test big-endian vs
   little-endian bitstring handling.
6. **Backends with a mock HTTP server:** use JDK `com.sun.net.httpserver.
   HttpServer` (no dependency) or WireMock (test-scope only) to stub each
   provider's submit/poll/results endpoints. Drive `IBMQuantumBackend` etc.
   through a full submit→poll(QUEUED→RUNNING→COMPLETED)→fetch cycle; assert the
   produced `Result`. Test timeout and FAILED paths.
7. **Bridge tests:** `toQiskit`/`fromQiskit` are thin; covered by (1).
8. **Optional integration tests** (disabled by default, env-gated): hit real
   provider sandboxes/simulators (IonQ simulator, IBM `ibmq_qasm_simulator`) when
   credentials env vars are present. Tagged `@Tag("integration")`.

---

## 7. Dependencies (minimal-dependency philosophy)

- **HTTP:** `java.net.http.HttpClient` (JDK built-in, since 11). No external HTTP
  lib. The project already targets JDK 17+ (per `ideas.md` §14 CI matrix).
- **JSON:** prefer a tiny hand-rolled reader/writer in `backend/HttpJson.java`
  (provider payloads are small and well-structured). If `jakarta.json`/
  `javax.json` is already available (the cloud stub used it), reuse it behind a
  thin interface. **Do not pull Jackson/Gson** unless the team decides otherwise.
- **AWS Braket / Azure Storage SDKs:** `optional` Maven scope, loaded
  reflectively or in a separate `strange-braket` / `strange-azure` module so the
  core jar stays dependency-light. Backends throw a clear error if the SDK is
  absent.
- **Test-only:** JUnit (already present), optionally WireMock/`HttpServer`.
- No Python runtime is added; the Qiskit bridge is QASM-string-mediated.

---

## 8. Risks

1. **Endianness mismatches** between Strange's state-vector bit ordering, QASM
   `q[i]`, and each provider's returned bitstrings. Highest-likelihood source of
   silent wrong results. Mitigation: WI-2 QuantumStep 4 analysis + round-trip and
   `ShotResultBuilder` endianness tests.
2. **Composite gate export** (Oracle/QFT/Add/Mul/Permutation/BlockGate). True
   arbitrary-unitary → QASM needs a decomposer that does not yet exist.
   Mitigation: lean on `Computations.decomposeStep`; otherwise throw clearly.
   Hard-blocked by **Section 4 (transpiler/decomposition)** for full coverage.
3. **Native-gate transpilation** (IBM `{id,rz,sx,x,cx}`, IonQ MS-basis). Without
   Section 4, rely on provider-side transpilation. Risk: some providers require
   pre-transpiled native gates. Mitigation: submit generic QASM3 + set provider
   transpile option; document which providers do server-side transpilation.
4. **Auth complexity:** AWS SigV4 and Azure AD token acquisition. Mitigation:
   optional SDKs / caller-supplied tokens; do not reinvent SigV4.
5. **Async contract leak:** blocking `runProgram` on a 30-minute hardware queue is
   surprising to callers expecting simulator speed. Mitigation: prominent Javadoc,
   default to the `Consumer`/future API in docs, configurable timeout.
6. **Missing gate classes** for QASM import (`s`, `t`, `cswap`, etc.). Hard
   dependency on **Section 1 (gate library completeness)**. Mitigation: import
   the subset that maps today; throw on the rest with a pointer to Section 1.
7. **`Result` contract divergence** (deterministic vector vs shot statistics) may
   confuse code that assumes exact amplitudes. Mitigation: documented per backend.

---

## 9. Suggested Sequencing

1. **WI-1 `GateNameTable`** (+ minimal `getTheta()` getters; coordinate with
   Section 1 parametric work).
2. **WI-2 OpenQASM 2.0 exporter** — highest immediate community value
   (`ideas.md` priority #3). Ship with the simulator-equivalence round-trip test.
3. **WI-4 importer** (lexer/parser/builder) — unlocks round-trip testing and the
   Qiskit bridge.
4. **WI-3 OpenQASM 3.0 exporter** and **WI-5 Quil exporter** (cheap once the
   walker exists).
5. **WI-12 Qiskit bridge** (thin wrapper; near-free after 2–4).
6. **WI-7 `ShotResultBuilder`** + **WI-6 `RemoteBackend`** base.
7. **WI-8 IBM** first (priority #9 in `ideas.md`), then **WI-9 IonQ** (simplest
   REST + free simulator for integration tests).
8. **WI-10 Braket** and **WI-11 Azure** last (optional SDK / auth complexity).
9. Backfill native-gate transpilation once **Section 4** lands; until then ship
   with provider-side transpilation.
