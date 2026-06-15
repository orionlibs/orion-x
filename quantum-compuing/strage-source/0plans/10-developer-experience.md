# Section 10 — Developer Experience: Implementation Plan

Scope: serialisation (JSON circuit format, round-trip `Program ↔ JSON`), logging/observability
(SLF4J migration + `StepListener` execution events), testing utilities (`QuantumAssert`,
`CircuitValidator`, property-based unitarity harness), and a benchmarking suite (JMH +
scalability chart generator).

QASM import/export is explicitly **out of scope** here — it lives in Section 6.

---

## 1. Overview & Goals

| Goal | Outcome |
|------|---------|
| Persist/share circuits | `Program` serialises to a stable JSON document and deserialises back to an equal `Program`. |
| Structured logging | All `System.err`/`System.out` diagnostics replaced by SLF4J (`slf4j-api` facade only — no binding shipped). |
| Observability | A `StepListener` hook fires per-step / per-gate / per-measurement so callers can watch execution without parsing logs. |
| Test ergonomics | `QuantumAssert` (state + unitary assertions) and `CircuitValidator` (bounds, measure-safety, causality) usable from JUnit. |
| Confidence | Property-based harness generates random circuits and asserts the composed step matrices are unitary. |
| Performance baselines | JMH microbenchmarks + a scalability chart generator (time vs n_qubits). |

Design constraints (carry the library's existing philosophy):
- **Minimal runtime dependencies.** The core library currently has none beyond the JDK. The only
  new *runtime compile* dependency we add is `org.slf4j:slf4j-api` (facade, zero transitive deps).
  JSON must be hand-rolled (no Jackson/Gson) so the core stays dependency-free. JMH and any
  JSON-schema/test deps are **test/benchmark scope only**.
- **No behavioural changes** to simulation. Logging and listeners are observational; serialisation
  is additive.
- **Strict typing**, mirror existing package layout (`org.redfx.strange` flat core, `org.redfx.strange.gate`,
  `org.redfx.strange.local`). New code goes in new packages described below.

---

## 2. Current State (cited)

### 2.1 Program model (what the serializer must capture)
`Program.java`:
- `private final int numberQubits;` (ctor `Program(int nQubits, Step... moreSteps)`).
- `private double[] initAlpha;` — per-qubit initial alpha, defaulted to `1d` for all qubits in the
  ctor (`Arrays.fill(initAlpha, 1d)`), settable via `initializeQubit(int idx, double alpha)`,
  readable via `getInitialAlphas()`. **Must be serialised** to faithfully reproduce non-`|0>`
  starts.
- `private final ArrayList<Step> steps;` exposed via `getSteps()`.
- `getNumberQubits()`, `getResult()/setResult()` (result is runtime output — **not** serialised).
- `decomposedSteps` is a `@Deprecated` cache (`getDecomposedSteps()/setDecomposedSteps()`) — a
  derived artifact populated by the simulator; **not** serialised.
- `addStep(Step)` runs `ensureMeasuresafe(step)` (private, lines 140–158) and throws
  `IllegalArgumentException("Adding a superposition step to a measured qubit")` on violation. The
  deserializer must funnel through `addStep`/`addSteps` so this invariant is enforced on load.

### 2.2 Step model
`Step.java`:
- `enum Type { NORMAL, PSEUDO, PROBABILITY }`; `private final Type type;`
- `private final ArrayList<Gate> gates;` via `getGates()` (unmodifiable).
- `private final String name;` (ctors: `Step(Gate...)` → name `"unknown"`; `Step(String, Gate...)`;
  `Step(Type)` → name `"pseudo"`).
- `index`, `complexStep`, `informal` — `index`/`complexStep` are assigned by `Program.addStep` via
  `setIndex` (derived; **not** serialised). `name`, `type`, `informal` **are** serialised.

### 2.3 Gate model (what distinguishes gate types)
`Gate.java` interface — relevant accessors: `getMainQubitIndex()`, `getAffectedQubitIndexes()`,
`getName()` (returns FQCN for `SingleQubitGate`, see below), `getMatrix()`, `getSize()`,
`setInverse(boolean)`. Static factories enumerate the serialisable gate vocabulary: `cnot, cz,
hadamard, identity, measurement, oracle, permutation, probability, swap, toffoli, x, y, z, rotation,
rotationX, rotationY, rotationZ`.

Gate parameter shapes observed:
- `SingleQubitGate` (`gate/SingleQubitGate.java`): single field `idx`; `getName()` returns
  `this.getClass().getName()`. Subclasses: `Hadamard, X, Y, Z, Identity, Measurement,
  ProbabilitiesGate, RotationX/Y/Z, Rotation`.
- `Rotation` (`gate/Rotation.java`): extends `SingleQubitGate`, carries `double thetav` and
  `enum Axes { XAxis, YAxis, ZAxis }`. `RotationX/Y/Z` are thin subclasses fixing the axis.
  **Parameters to capture: `theta`, `axis`.**
- `TwoQubitGate` (`gate/TwoQubitGate.java`): `getMainQubitIndex()` + `getSecondQubitIndex()`.
  Subclasses: `Cnot, Cz, Swap` (+ `Cr` which also carries an angle). **Capture both indices.**
- `ThreeQubitGate`: `Toffoli` (3 indices).
- `PermutationGate`: `permutation(int a, int b, int n)` — capture `index1, index2, n`.
- `Oracle`: carries a raw `Complex[][]` matrix (`oracle(Complex[][])`) — must serialise the matrix.
- `BlockGate`/`ControlledBlockGate`/Fourier/Add*/Mul* — see §3.5 for the registry strategy and
  the v1 fallback (serialise composite/opaque gates by their explicit matrix).

### 2.4 Execution loop (where StepListener hooks go)
`local/SimpleQuantumExecutionEnvironment.java`:
- `runProgram(Program p)` builds the initial `probs` vector from `initAlpha`, decomposes steps
  (`Computations.decomposeStep`), then the main loop (lines 102–117):
  ```java
  for (Step step : simpleSteps) {
      if (!step.getGates().isEmpty()) {
          probs = applyStep(step, probs, qubit);
          int idx = step.getComplexStep();
          if (idx > -1) result.setIntermediateProbability(idx, probs);
      }
  }
  ```
  This loop is the single insertion point for `StepListener.beforeStep/afterStep` and
  probability-snapshot events.
- Already uses `java.util.logging.Logger LOG` for trace (`LOG.info/fine/finer/finest`). Line 109
  `LOG.info("after this step, probs = "+probs)` and line 119 `printProbs(probs)` are noisy
  diagnostics to route through the listener / SLF4J.

### 2.5 println inventory (logging migration target)
`grep "System.err|System.out"`:

| File | Count | Disposition |
|------|------:|-------------|
| `demo/Demo.java` | 19 | **Leave as-is** — demo program, stdout is the intended UX. Out of migration scope. |
| `algorithm/Classic.java` | 10 | Migrate. Sites: lines 151, 196, 222, 237, 239, 241, 245, 249, 251, 257 (mix of progress narration and warnings). |
| `Complex.java` | 7 | `printArray`/`printMatrix`/`dbg` are explicit debug-print helpers taking a `PrintStream`. **Leave** the `PrintStream` API; route the no-arg `dbg`/default overloads through SLF4J `trace`. |
| `local/Computations.java` | 5 | Migrate diagnostic prints to SLF4J. |
| `Result.java` | 5 | `printInfo()` writes to stdout (lines 227–233) — user-facing report. **Keep** `printInfo()` (public API) but add SLF4J for any internal diagnostics. |
| `Program.java` | 5 | `printInfo()` (lines 224–230) — same treatment as `Result.printInfo()`. Keep. |
| `gate/AddModulus.java` | 2 | Migrate. |
| `local/SimpleQuantumExecutionEnvironment.java` | 1 | Commented-out `System.err` (line 112). Delete the dead comment. |
| `gate/ThreeQubitGate.java` | 1 | Migrate. |
| `cloud/ResultConverter.java` | 1 | Migrate. |
| `ControlledBlockGate.java` | 1 | Migrate. |

Net rule: **diagnostic** prints → SLF4J; **explicit user-facing report APIs** (`printInfo`,
`Complex.print*(PrintStream)`) stay. `SimpleQuantumExecutionEnvironment` and `Computations` also
currently use `java.util.logging` — convert those to SLF4J too for one consistent facade.

> Note: this source tree is a flat extract with **no `pom.xml`/`build.gradle` present**. Wherever
> this plan says "add dependency", it means in the real Strange Maven/Gradle build; the dependency
> coordinates are listed so the build owner can apply them.

---

## 3. Work Item: JSON Serialisation

### 3.1 Dependency decision
Hand-roll a tiny reader/writer. Rationale: the core library is dependency-free; pulling Jackson
(~2MB, transitive) or Gson contradicts that. Our schema is small and closed. We add:
- `org.redfx.strange.serialize.JsonWriter` — minimal serializer (objects, arrays, strings, numbers,
  bools, null) with deterministic key ordering and proper string escaping.
- `org.redfx.strange.serialize.JsonReader` — minimal recursive-descent parser producing
  `Map<String,Object>` / `List<Object>` / `String` / `Double` / `Boolean` / `null`.
Both are ~150 LOC each, no external deps. (YAML deferred — see §3.7. JSON is the load-bearing
format; YAML is a thin nicety.)

### 3.2 Files
```
serialize/ProgramSerializer.java     // Program -> JSON String / Writer / File
serialize/ProgramDeserializer.java   // JSON String / Reader / File -> Program
serialize/GateCodec.java             // per-gate-type encode/decode registry
serialize/JsonWriter.java            // minimal JSON emitter
serialize/JsonReader.java            // minimal JSON parser
serialize/SerializationException.java
```
Package `org.redfx.strange.serialize`.

### 3.3 JSON schema (v1)
```jsonc
{
  "format": "strange-circuit",
  "version": 1,
  "numberQubits": 3,
  "initialAlphas": [1.0, 1.0, 1.0],          // length == numberQubits; from getInitialAlphas()
  "steps": [
    {
      "name": "unknown",                      // Step.getName()
      "type": "NORMAL",                       // Step.Type: NORMAL | PSEUDO | PROBABILITY
      "informal": false,                      // Step.isInformal()
      "gates": [
        { "type": "hadamard", "qubit": 0 },
        { "type": "cnot", "control": 0, "target": 1 },
        { "type": "rotation", "axis": "ZAxis", "theta": 1.5707963267948966, "qubit": 2,
          "inverse": false },
        { "type": "permutation", "index1": 0, "index2": 2, "n": 3 },
        { "type": "oracle", "matrix": [[[1.0,0.0],[0.0,0.0]], [[0.0,0.0],[1.0,0.0]]] }
      ]
    }
  ]
}
```
Conventions:
- Complex numbers serialise as `[re, im]` pairs; matrices as nested arrays of those pairs.
- `index`, `complexStep`, `decomposedSteps`, and `Result` are **derived/runtime** and never written.
- Per-gate `qubit`/`control`/`target` keys are explicit (not a raw index array) so the format is
  human-readable and self-documenting.

### 3.4 Gate type tags
Lower-case, matching the `Gate` static factories:

| type tag | params | maps to |
|----------|--------|---------|
| `hadamard`,`x`,`y`,`z`,`identity`,`measurement`,`probability` | `qubit` | single-qubit gate ctor `(idx)` |
| `rotation` | `axis`,`theta`,`qubit`,`inverse` | `new Rotation(theta, Axes.valueOf(axis), qubit)` |
| `rotationX`/`rotationY`/`rotationZ` | `theta`,`qubit` | respective ctor |
| `cnot`,`cz`,`swap` | `control`,`target` (or `first`,`second` for swap) | two-qubit ctor `(a,b)` |
| `cr` | `control`,`target`,`theta` | `Cr` ctor |
| `toffoli` | `control1`,`control2`,`target` | `Toffoli(a,b,c)` |
| `permutation` | `index1`,`index2`,`n` | `PermutationGate(a,b,n)` |
| `oracle` | `matrix` | `new Oracle(Complex[][])` |
| `block` / `composite` (fallback) | `size`,`matrix`,`mainQubit`,`affected[]` | opaque `Oracle`-style reconstruction (§3.5) |

### 3.5 Gate registry & composite fallback (`GateCodec`)
`GateCodec` holds two maps: `encoders: Class<? extends Gate> -> Function<Gate, Map<String,Object>>`
and `decoders: String -> Function<Map<String,Object>, Gate>`. The well-known gates above register
explicit codecs. For gates **not** in the registry (Fourier/InvFourier, Add*/Mul*, BlockGate,
ControlledBlockGate, InformalGate), v1 uses the **matrix fallback**: encode `type:"composite"` with
the gate's `getMatrix()`, `getSize()`, `getMainQubitIndex()`, and `getAffectedQubitIndexes()`.
Decode reconstructs them as an `Oracle`-backed opaque gate that replays the matrix. This guarantees
*executable* round-trips even before every gate has a named codec. Named codecs for the arithmetic
gates can be added incrementally without schema changes.

### 3.6 Serializer / deserializer API
```java
public final class ProgramSerializer {
    public static String toJson(Program program);
    public static void   write(Program program, Writer out) throws IOException;
    public static void   write(Program program, Path file) throws IOException;
}
public final class ProgramDeserializer {
    public static Program fromJson(String json);
    public static Program read(Reader in) throws IOException;
    public static Program read(Path file) throws IOException;
}
```
Deserialization flow: parse JSON → validate `format`/`version` → `new Program(numberQubits)` →
apply `initializeQubit(idx, alpha)` for each non-default alpha → for each step JSON, build `Step`
(choosing the `Type`/name ctor, set `informal`), decode gates via `GateCodec`, `addGates(...)`,
then `program.addStep(step)` (so `ensureMeasuresafe` enforces invariants on load). Malformed input
throws `SerializationException` with the offending path.

### 3.7 YAML (deferred, thin layer)
Provide `ProgramYaml.toYaml/fromYaml` later as a *format adapter* over the same intermediate
`Map`/`List` tree — a minimal flow-style YAML emitter/parser, still dependency-free. Marked
optional; JSON is the canonical format and the round-trip test target.

---

## 4. Work Item: SLF4J Migration

### 4.1 Dependency
Add `org.slf4j:slf4j-api` (current 2.0.x) as the **only** new runtime/compile dependency of the
core library. Ship **no binding** (no logback/slf4j-simple) so downstream apps pick their own;
tests may add `slf4j-simple` in test scope.

### 4.2 Per-file pattern
Each migrated class gets:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger LOG = LoggerFactory.getLogger(<Class>.class);
```
Replace:
- `System.err.println(x)` → `LOG.warn("{}", x)` (or `LOG.error` for genuine error paths).
- `System.out.println(x)` diagnostics → `LOG.info("{}", x)` / `LOG.debug`.
- existing `java.util.logging` (`SimpleQuantumExecutionEnvironment`, `Computations`) → SLF4J
  equivalents (`info→info`, `fine→debug`, `finer/finest→trace`).
- Use SLF4J `{}` parameterised messages — never string concatenation — to avoid eager cost at
  suppressed levels (the per-step `LOG.info("after this step, probs = "+probs)` at line 109 is a
  prime offender; downgrade to `LOG.trace("after step probs={}", (Object) probs)`).

### 4.3 Files to change (from §2.5)
Migrate: `algorithm/Classic.java`, `local/Computations.java`, `gate/AddModulus.java`,
`gate/ThreeQubitGate.java`, `cloud/ResultConverter.java`, `ControlledBlockGate.java`,
`local/SimpleQuantumExecutionEnvironment.java` (jul→slf4j + delete dead comment line 112),
`Complex.java` (only the no-arg `dbg`/default-stream diagnostics; keep `print*(PrintStream)` API).
**Do not** touch: `demo/Demo.java` (intentional stdout), `Program.printInfo()`,
`Result.printInfo()` (public report APIs).

### 4.4 Classic.java specifics
Classify the 10 sites: the "bummer/restart/too many tries/measured periodicity of 0" messages
(151, 222, 245, 251, 257) → `LOG.warn`; the progress narration (196, 237, 239, 241, 249) →
`LOG.info`. Better still, surface these as part of the future `AlgorithmResult` (Section 3 item),
but for this section a faithful SLF4J swap is sufficient.

---

## 5. Work Item: StepListener Hooks

### 5.1 Interface (default methods so adoption is opt-in)
```java
package org.redfx.strange.observe;

public interface StepListener {
    default void onProgramStart(Program program, int dimension) {}
    default void beforeStep(int stepIndex, Step step) {}
    default void onGateApplied(int stepIndex, Gate gate) {}
    default void onQubitMeasured(int stepIndex, int qubitIndex, boolean value) {}
    default void onProbabilitySnapshot(int stepIndex, Complex[] amplitudes) {}
    default void afterStep(int stepIndex, Step step, Complex[] amplitudes) {}
    default void onProgramEnd(Result result) {}
}
```
A `LoggingStepListener implements StepListener` that forwards every event to SLF4J `debug` ships as
the reference implementation — this is how the verbose per-step probability logging (currently
inline in `runProgram`) is delivered without coupling the engine to a logger.

### 5.2 Wiring into the engine
- Add to `QuantumExecutionEnvironment` (default no-op so existing impls/cloud stub are unaffected):
  ```java
  default void addStepListener(StepListener l) {}
  default void removeStepListener(StepListener l) {}
  ```
- `SimpleQuantumExecutionEnvironment` holds `private final List<StepListener> listeners = new CopyOnWriteArrayList<>();`
  and implements add/remove. In `runProgram`:
  - after building `probs`: fire `onProgramStart(p, dim)` and `onProbabilitySnapshot(0, probs)`.
  - inside the loop (lines 102–117), wrap the body: `beforeStep(cnt, step)` → for each
    `gate` in `step.getGates()` fire `onGateApplied(cnt, gate)`; if a gate `instanceof Measurement`
    fire `onQubitMeasured` after `applyStep` using the measured qubit index → `applyStep` →
    `afterStep(cnt, step, probs)` and `onProbabilitySnapshot(idx, probs)` when `idx > -1`.
  - after the loop: `onProgramEnd(result)`.
- Listener exceptions are caught and logged (one bad listener must not abort a run).

This is purely additive and observational — no change to `probs` math or `Result`.

---

## 6. Work Item: QuantumAssert

Package `org.redfx.strange.test`. Static assertion helpers (throw `AssertionError`, JUnit-agnostic).

```java
public final class QuantumAssert {

    // amplitude comparison of the final probability vector
    public static void assertStateEquals(Result actual, Complex[] expected, double epsilon);
    public static void assertStateEquals(Complex[] actual, Complex[] expected, double epsilon);

    // probability (|amp|^2) comparison — uses Result.getProbability()
    public static void assertProbabilitiesEqual(Result actual, double[] expectedProbs, double epsilon);

    // global-phase-insensitive variant (states equal up to e^{i phi})
    public static void assertStateEqualsIgnoringGlobalPhase(Complex[] actual, Complex[] expected, double epsilon);

    // unitarity of a single gate's matrix
    public static void assertUnitary(Gate gate, double epsilon);
    public static void assertUnitary(Complex[][] matrix, double epsilon);
    public static void assertUnitary(Gate gate); // epsilon = 1e-10
}
```

`assertStateEquals(Result, ...)` reads `actual.getProbability()` (the `Complex[]` final amplitudes
on `Result`) and compares element-wise: `|a.r-e.r| <= eps && |a.i-e.i| <= eps`; mismatched lengths
fail immediately with a clear message including both lengths.

`assertUnitary` implementation (uses **existing** `Complex` matrix ops, no new math):
```java
Complex[][] u  = matrix;                       // gate.getMatrix()
Complex[][] ud = Complex.conjugateTranspose(u);// existing static
Complex[][] p  = Complex.mmul(u, ud);          // existing static
Complex[][] id = Complex.identityMatrix(u.length); // existing static
// assert square, then assert p ~= id within epsilon (per-entry r & i)
```
Failure messages report the first `(row,col)` whose `U·U†` entry deviates from the identity, with
the actual deviation, so a non-unitary gate is immediately diagnosable.

---

## 7. Work Item: CircuitValidator

Package `org.redfx.strange.test` (or `org.redfx.strange.validate`). Read-only static analysis of a
`Program` — does **not** execute it.

```java
public final class CircuitValidator {
    public static List<ValidationIssue> validate(Program program);
    public static void requireValid(Program program); // throws IllegalArgumentException if any ERROR issues
}
public record ValidationIssue(Severity severity, int stepIndex, String message) {
    public enum Severity { ERROR, WARNING }
}
```

Checks:
1. **Qubit-index bounds** — for every `Step`, every `Gate`, every index in
   `gate.getAffectedQubitIndexes()` (and `getHighestAffectedQubitIndex()`), assert
   `0 <= idx < program.getNumberQubits()`. Out-of-range ⇒ ERROR.
2. **Measure-safety** — generalise the private `Program.ensureMeasuresafe` (currently only inspects
   `Hadamard` and `Cnot`'s second qubit) into a reusable static pass: walk steps in order, track the
   set of measured qubits (any `Measurement` gate's main qubit). If a *later* step applies a
   superposition-creating gate to an already-measured qubit, emit ERROR. v1 keeps parity with the
   existing logic (Hadamard main qubit, Cnot target) and is structured so additional
   superposition-introducing gates can be added centrally. This is the canonical home; `Program`
   can later delegate to it.
3. **Causality / ordering** — within a single `Step`, no two gates may touch the same qubit (mirrors
   `Step.verifyUnique`, but validated at program level as a defensive WARNING in case a step was
   built bypassing `addGate`); flag a measurement followed by a controlled gate whose *control* is
   the measured qubit as a WARNING (semantically a classically-controlled op — informational).
4. **Empty / pseudo** steps are skipped (consistent with the engine ignoring empty steps).

---

## 8. Work Item: Property-Based Unitarity Harness

Package `org.redfx.strange.test`. Goal: random circuits ⇒ the composed per-step operation is
unitary (sanity-checks the gate library + step matrix construction).

```java
public final class RandomCircuits {
    public static Program randomProgram(int nQubits, int nSteps, long seed);
    // draws gates from a unitary-only pool: H,X,Y,Z,Rx,Ry,Rz,Cnot,Cz,Swap,Toffoli
    // (excludes Measurement / ProbabilitiesGate which are non-unitary observation ops)
}
```
Test (`RandomCircuitUnitarityTest`):
- For seeds `0..N`: build a random `Program`, then for each `Step` compute its full operation
  matrix via `Computations.calculateStepMatrix(gates, nQubits, env)` and assert
  `QuantumAssert.assertUnitary(stepMatrix, 1e-9)`.
- Also assert the program runs (`new SimpleQuantumExecutionEnvironment().runProgram(p)`) and the
  output probability vector sums to ~1 (norm preservation) — a unitarity check at the state level.
Seeded RNG ⇒ deterministic, reproducible failures.

---

## 9. Work Item: JMH Microbenchmarks

JMH cannot live in the dependency-free core. Create a separate **benchmark source set / module**
(`strange-benchmarks`, or `src/jmh` via the gradle-jmh plugin in the real build) depending on the
core. Coordinates: `org.openjdk.jmh:jmh-core` + `jmh-generator-annprocess` (benchmark scope only).

Benchmarks (`org.redfx.strange.bench`):
- `MatrixMultiplyBenchmark` — `Complex.mmul` over square matrices sized `2^k` (k=1..6),
  `@Param` on dimension.
- `StateVectorUpdateBenchmark` — one `applyStep` / `Computations.calculateNewState(gates, vector, n)`
  for a single gate across `@Param` n_qubits = {4,8,12,16,18}.
- `QftBenchmark` — full `Fourier`/`InvFourier` over a register, `@Param` n_qubits.
- `ProgramRunBenchmark` — end-to-end `SimpleQuantumExecutionEnvironment.runProgram` of a fixed
  reference circuit (e.g. Grover/QFT), `@Param` n_qubits.

Standard JMH config: `@BenchmarkMode(Throughput|AverageTime)`, `@OutputTimeUnit`, warmup/measure
iterations, `@State(Scope.Thread)` setup that pre-builds the matrices/vectors so allocation isn't
timed. Emit JSON results (`-rf json -rff results.json`) for the chart generator.

---

## 10. Work Item: Scalability Chart Generator

`org.redfx.strange.bench.ScalabilityChart` (benchmark module). Two modes:
1. **Self-timed sweep** (no JMH dependency at runtime): run a chosen circuit for
   n_qubits = 1..maxQ, time `runProgram` with `System.nanoTime()` (median of K runs), and emit:
   - `CSV` (`n_qubits,median_ns,bytes_state_vector`) — primary, dependency-free, plots in any tool.
   - optional `SVG` line chart hand-emitted (no charting lib) — log-scale y, time vs n_qubits, one
     series per `QuantumExecutionEnvironment` impl supplied (future-proofs for MPS/stabilizer sims).
2. **JMH-results mode**: parse the JMH `results.json` (reusing `serialize.JsonReader`) and render
   the same CSV/SVG. This keeps the chart generator dependency-free while consuming JMH output.

API:
```java
public static void sweep(QuantumExecutionEnvironment env,
                         IntFunction<Program> circuitFactory,
                         int maxQubits, int repeats, Path csvOut);
public static void renderSvg(Path csvIn, Path svgOut);
```

---

## 11. Testing Strategy

- **Round-trip serialisation equality** (`ProgramRoundTripTest`): for a corpus of programs (Bell
  pair, GHZ, QFT(3), a rotation-heavy circuit, an oracle circuit, a program with non-default
  `initializeQubit`):
  `Program p2 = ProgramDeserializer.fromJson(ProgramSerializer.toJson(p));` then assert structural
  equality: `numberQubits`, `Arrays.equals(getInitialAlphas())`, `steps.size()`, per-step
  `name`/`type`/`informal`, per-gate `type`+indices+params, and — the strongest check — that both
  programs produce element-wise-equal probability vectors when run
  (`QuantumAssert.assertStateEquals`, eps 1e-12). Idempotence: `toJson(p) == toJson(p2)`.
- **assertUnitary on all gates** (`AllGatesUnitaryTest`): instantiate every concrete `Gate` from the
  `Gate` static factories (and direct ctors for the rest) and assert `QuantumAssert.assertUnitary`.
  Exclude the non-unitary observation gates (`Measurement`, `ImmediateMeasurement`,
  `ProbabilitiesGate`) explicitly with a comment on why.
- **Property-based** (§8): seeded random circuits, step-matrix unitarity + norm preservation.
- **CircuitValidator tests**: out-of-bounds index ⇒ ERROR; measure-then-Hadamard-same-qubit ⇒ ERROR
  (parity with `ensureMeasuresafe`); valid circuit ⇒ empty issue list.
- **StepListener test**: register a recording listener, run a 2-step program, assert the event
  sequence (`onProgramStart`, `beforeStep`/`onGateApplied`×g/`afterStep` per step, `onProgramEnd`)
  and that listener exceptions don't abort the run.
- **SLF4J**: tests run with `slf4j-simple` (test scope) and assert no `System.err`/`System.out`
  writes leak from migrated classes (capture streams in a focused test, or rely on review +
  `grep` gate in CI).

---

## 12. Risks

| Risk | Mitigation |
|------|-----------|
| **New runtime dependency** (`slf4j-api`) conflicts with the "zero-dep core" ethos. | `slf4j-api` is the de-facto Java logging facade, zero transitive deps, no binding shipped — minimal footprint and standard practice. Documented as the *only* runtime addition. |
| **JSON hand-roll bugs** (escaping, number precision, unicode). | Keep parser/writer tiny and fully unit-tested; serialise doubles with `Double.toString` (round-trip-exact) not formatted output; cover escaping edge cases. |
| **Composite/opaque gate fallback loses semantic identity** (a Fourier becomes a matrix blob). | Acceptable for v1 (executable round-trip preserved); add named codecs incrementally; schema versioned (`"version":1`) so upgrades are detectable. |
| **`getName()` returns FQCN** for `SingleQubitGate`, tempting to key codecs off it. | Use the explicit lower-case `type` tags in `GateCodec`, not class names, so refactors/relocations don't break the format. |
| **Listener overhead / exceptions** in the hot loop. | No-op defaults; `CopyOnWriteArrayList`; per-listener try/catch; verbose snapshots only when listeners are registered. |
| **JMH/test deps leaking into core artifact.** | JMH, JSON-schema, `slf4j-simple` strictly benchmark/test scope; chart generator + benchmarks in a separate module. |
| **`ensureMeasuresafe` divergence** between `Program` (private) and `CircuitValidator`. | Make `CircuitValidator` the canonical implementation and have `Program.addStep` delegate to it (small, reviewed change) so there is one source of truth. |
| **No build file in this extract.** | Dependency coordinates listed explicitly; build owner applies them to the real `pom.xml`/`build.gradle`. |

---

## 13. Suggested Sequencing

1. **SLF4J migration** (§4) — mechanical, unblocks clean diagnostics, no API surface. Do first so
   later work doesn't add new `println`s.
2. **StepListener** (§5) — depends on (1) for `LoggingStepListener`; small, additive engine change.
3. **QuantumAssert** (§6) — pure, no deps on the above; needed by every subsequent test.
4. **CircuitValidator** (§7) + fold `ensureMeasuresafe` into it.
5. **JSON serialisation** (§3) — uses `QuantumAssert` for its round-trip tests.
6. **Property-based harness** (§8) — uses `QuantumAssert` + `RandomCircuits`.
7. **JMH benchmarks** (§9) — separate module; independent, can proceed in parallel after (1).
8. **Scalability chart generator** (§10) — consumes JMH output / self-timed sweep; last.
9. **YAML adapter** (§3.7) — optional, after JSON is stable.
