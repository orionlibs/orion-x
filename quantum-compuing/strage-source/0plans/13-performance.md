# Section 13 — Performance & Scalability: Implementation Plan

Package: `org.redfx.strange`. This plan covers four work items: (1) `Complex`
representation benchmark + migration including a Structure-of-Arrays (SoA)
refactor of the hot path, (2) a lazy, invalidation-aware decomposition cache to
replace the deprecated `getDecomposedSteps()/setDecomposedSteps()`, (3)
configurable amplitude pruning during state evolution, and (4) distributed
state-vector partitioning across JVMs.

Section 2 (simulation engine: sparse/MPS/stabilizer/GPU/parallel CPU) also
touches `Computations`. This plan deliberately stays in the lane of
*representation, caching, pruning, and distribution* and does **not** introduce
new simulator types. Where the two sections share an artifact (notably the SoA
state-vector API and the per-step apply loop), the contract is defined here so
Section 2 can build on top of it.

---

## 1. Overview & Goals

Goals, in priority order:

1. **No public-API regression.** `Complex` is part of the public surface
   (`public final class Complex`, public fields `r`, `i`, public constructors,
   public arithmetic, public static matrix helpers). Gate authors and external
   code construct `Complex` and read `c.r`/`c.i`. Any representation change must
   keep `Complex` compilable and behaviourally identical for existing callers.
2. **Measure before migrating.** Stand up a JMH harness (cross-ref Section 10
   "Benchmarking suite") that exercises the real hot path, and only adopt a new
   representation if it shows a reproducible win at representative qubit counts
   (12–24 qubits).
3. **Lazy, correct caching.** Decomposition must be computed on demand, cached,
   and invalidated exactly when the program changes — without the deprecated
   public mutator.
4. **Approximate scaling.** Amplitude pruning lets larger, low-entanglement
   circuits run, with an explicit, documented error/normalisation contract.
5. **Horizontal scaling.** Distributed partitioning splits the 2^n vector across
   nodes so circuits that exceed a single JVM heap become runnable.

Non-goals: new simulator algorithms (Section 2), GPU offload (Section 2),
multi-threaded single-node matrix multiply (Section 2 — though the SoA layout
here is a prerequisite that makes it tractable).

---

## 2. Current Hot-Path Analysis

### 2.1 `Complex.java` (representation)

- **Storage is `float`, not `double`.** Lines 69–70:
  ```java
  public float r;
  public float i;
  ```
  The constructor `Complex(double r, double i)` (lines 87–90) narrows to float:
  `this.r = (float) r; this.i = (float) i;`. **This is a latent accuracy issue
  and contradicts `ideas.md` §13 which assumes `double`.** The benchmark must
  evaluate both precision *and* speed, and the migration is an opportunity to
  decide float-vs-double deliberately rather than by accident.
- **Immutable-object arithmetic allocates.** `add` (98–102), `min` (137–141),
  `mul(Complex)` (149–153), and `mul(double)` (161–163) each `return new
  Complex(...)`. In the matrix-vector inner loop this is one allocation per
  scalar multiply-add.
- **In-place variants already exist** and are the intended fast path but are
  underused: `addr` (110–114) and `addmulr(a,b)` (123–129), which computes
  `this += a*b` with no allocation. The hot loops in `Computations` mostly use
  the allocating `add`/`mul` pair instead.
- **Sentinel identity comparisons.** `ZERO`, `ONE`, `I`, `HC`, `HCN` are shared
  singletons (54–67). `tensor` (201–219) and `permutate0` (289–303) compare with
  `== Complex.ZERO` / `.equals(Complex.ZERO)`. Any representation change that
  stops returning these exact singletons (e.g. an SoA path that synthesises
  fresh `Complex` objects) must preserve these comparisons' semantics or migrate
  the call sites.
- **Static matrix helpers** (`identityMatrix`, `tensor`, `mmul`/`slowmmul`,
  `conjugateTranspose`, `permutate*`) operate on `Complex[][]`. These are gate
  matrices, typically tiny (2×2, 4×4, 8×8) and built once per gate; they are
  **not** the dominant cost and can keep the object representation.

### 2.2 `local/Computations.java` (the hot path)

The dominant cost is the matrix-vector product in **`getNextProbability2`**
(479–569). Three inner loops do per-element `Complex` work:

- The "only identity" sub-block (498–524): for each partition `j`, gathers
  `oldv`, then `newv[i] = newv[i].add(matrix[i][k].mul(oldv[k]))` (516) — an
  allocating `mul` then an allocating `add`, O(gatedim²) per partition.
- The general recursion block (528–549): builds `vsub` recursively, then
  `answer[...] = answer[...].add(matrix[i][k].mul(vsub[k][j]))` (544) — same
  allocating pattern, O(gatedim² · partdim).
- The single-gate base case (550–567): `answer[i] = answer[i].add(matrix[i][j].
  mul(v[j]))` (563), O(size²).

Every iteration allocates two `Complex` objects (`mul` result + `add` result)
that are immediately discarded. At n qubits the state vector is `2^n` `Complex`
*objects* (`Complex[] vector`), i.e. `2^n` heap headers + boxed pointers, not a
packed primitive buffer — poor cache locality and heavy GC pressure. This is the
single biggest lever.

Also relevant:

- **`calculateNewState`** (450–458) is the entry point from the simulator.
- **`getNextProbability`** (460–467) special-cases `Swap` via `processSwapGate`
  (469–477), which is pure index permutation (no arithmetic) — a model for
  communication-free ops in the distributed design.
- **`permutateVector`** (419–438) is also pure permutation.
- **`calculateQubitStatesFromVector`** (680–695) and `doImmediateMeasurement`
  (707–730) read `vector[i].abssqr()` — these are reductions over the whole
  vector and matter for the distributed (allreduce) design.

### 2.3 `Program.java` (decomposition cache)

- Field (62): `private List<Step> decomposedSteps = null;`
- Invalidation (124), inside `addStep`: `this.decomposedSteps = null;` — set to
  null on every structural mutation. `addSteps` (134–138) loops `addStep`, so it
  invalidates per-step.
- Deprecated accessors:
  - `@Deprecated public List<Step> getDecomposedSteps()` (175–178) — returns the
    raw cache, possibly `null`.
  - `@Deprecated public void setDecomposedSteps(List<Step> ds)` (185–188) —
    lets external code (the simulator) write the cache.

### 2.4 `local/SimpleQuantumExecutionEnvironment.java` (current cache usage)

`runProgram` (65–127) currently *drives* the cache from outside the model
(90–97):

```java
List<Step> simpleSteps = p.getDecomposedSteps();
if (simpleSteps == null) {
    simpleSteps = new ArrayList<>();
    for (Step QuantumStep : QuantumSteps) {
        simpleSteps.addAll(Computations.decomposeStep(step, nQubits));
    }
    p.setDecomposedSteps(simpleSteps);
}
```

This is the lazy-compute-then-store pattern, but implemented in the wrong place
(the simulator) using the deprecated mutator. The per-step apply loop is 102–117;
`applyStep` (145–166) dispatches to `Computations.calculateNewState` (161) or to
`permutateVector` (156). There is a dead helper `decomposeSteps` (141–143) that
just returns its argument — remove during this work.

---

## 3. Detailed Work Items

Each item lists target files, the change, and a code sketch. Items are ordered
so each lands independently behind tests.

### WI-1 — Lazy decomposition cache (lowest risk, do first)

**Files:** `Program.java`, `local/SimpleQuantumExecutionEnvironment.java`.

**Idea:** move ownership of decomposition into `Program` as a lazily computed,
self-invalidating value. The decomposition is a pure function of `(steps,
numberQubits)`, so the program can compute and memoise it, and clear the memo in
`addStep` exactly where `this.decomposedSteps = null;` already lives (line 124).

**`Program.java` changes:**

```java
// keep the field, drop the public setter from the API
private List<Step> decomposedSteps = null;

/**
 * Lazily computes (and caches) the permutation-free decomposition of this
 * program's QuantumSteps. The cache is invalidated by {@link #addStep}.
 */
public List<Step> getDecomposedSteps() {              // de-deprecated
    List<Step> ds = this.decomposedSteps;
    if (ds == null) {
        ds = new ArrayList<>();
        for (Step QuantumStep : QuantumSteps) {
            ds.addAll(Computations.decomposeStep(step, numberQubits));
        }
        this.decomposedSteps = Collections.unmodifiableList(ds);
    }
    return this.decomposedSteps;
}
```

- Invalidation stays exactly at line 124 (`this.decomposedSteps = null;` in
  `addStep`). Document with an inline comment that this line is the single
  invalidation point and must clear the cache on every structural change.
- **`setDecomposedSteps` retention:** keep it `@Deprecated` for one release as a
  no-op-safe shim (it can still set the field) so external callers do not break,
  but mark it for removal and stop using it internally. New code never calls it.
- Decomposition mutates `Step` state (`decomposeStep` calls
  `s.setComplexStep(...)`, `s.setInformalStep(...)`, and inserts permutation
  `Step`s). Because the result is memoised and the inputs are the same `Step`
  objects, recomputation must be idempotent. **Verification QuantumStep:** confirm
  `decomposeStep` is idempotent on an already-decomposed `Step`, or guard
  recomputation so the cache is only ever built once per `(steps)` generation.
  If it is *not* idempotent (a real risk given the in-place
  `setComplexStep`/`addGate` mutations), the safe design is to keep
  `decomposedSteps` as the canonical computed value and never re-run
  `decomposeStep` on the same `Step` instances — i.e. compute once, freeze.

**`SimpleQuantumExecutionEnvironment.java` changes:**

Replace the inline block (90–97) with a single call and delete the dead
`decomposeSteps` helper (141–143):

```java
List<Step> simpleSteps = p.getDecomposedSteps(); // now never null, lazily built
```

**Why this ordering:** it is self-contained, removes a deprecated public mutator
from normal use, and gives every later work item (pruning, distribution) a clean
single source of the executable QuantumStep list.

### WI-2 — `Complex` representation benchmark

**Files (new):** `bench/` JMH module (see §5). No production change yet — this
item is purely measurement and produces the decision input for WI-3.

Candidate representations to benchmark on the inner multiply-add of
`getNextProbability2`:

1. **Baseline:** current `Complex` object with `float` fields, allocating
   `add`/`mul`.
2. **Baseline + in-place:** same objects but rewrite inner loops to use the
   existing `addmulr` (123–129) / `addr` (110–114) so no allocation per
   iteration. (Cheapest possible win — measure it before anything exotic.)
3. **`record Complex(double r, double i)`** — value-semantics record, `double`
   precision. Still heap-allocated under current JVMs but JIT-friendly and a
   QuantumStepping stone to Valhalla.
4. **Split primitive `double[]` SoA** — two parallel arrays `double[] re,
   double[] im` for the state vector (and optionally gate matrices). No
   per-amplitude object; sequential memory; auto-vectorisable. Expected winner.
5. **`float[]` SoA** — same as 4 but single precision, matching today's actual
   precision, half the memory bandwidth.
6. **Valhalla value class** (`value class Complex { double r; double i; }`) under
   a JDK with `--enable-preview` value classes — flattened, no heap header.
   Gated behind a JDK-availability profile; measured if the build JDK supports
   it, otherwise reported as "pending toolchain".

Metrics per representation: throughput (ops/s), ns/op, allocation rate
(`-prof gc`), and an end-to-end "apply N random gates to n-qubit state" wall
time at n ∈ {12, 16, 20, 24}. Also record max-error vs a `double`-reference run
to quantify the float-vs-double accuracy tradeoff.

### WI-3 — SoA refactor of the state vector (the migration)

**Files:** `local/Computations.java` (hot loops), a new
`local/StateVector.java`, `local/SimpleQuantumExecutionEnvironment.java`. **No
change to `Complex.java`'s public API.**

This is the migration that keeps the public API stable while switching the *hot
path* off the object representation. Strategy: introduce an internal SoA
container; convert at the simulator boundary; keep `Complex[]` only at public
seams.

**New `StateVector` (internal, package-private):**

```java
final class StateVector {          // SoA: cache-local, vectorisable
    final double[] re;             // (or float[] per WI-2 outcome)
    final double[] im;
    StateVector(int dim) { re = new double[dim]; im = new double[dim]; }

    static StateVector fromComplex(Complex[] v) { /* unpack once */ }
    Complex[] toComplex() { /* repack once, for public Result/Gate seams */ }
    int length() { return re.length; }
}
```

**Hot-loop rewrite** (the three sites in `getNextProbability2`): replace
`newv[i] = newv[i].add(matrix[i][k].mul(oldv[k]))` with primitive fused
multiply-add on the SoA arrays, e.g. the base case (550–567) becomes:

```java
for (int i = 0; i < size; i++) {
    double ar = 0, ai = 0;
    for (int j = 0; j < size; j++) {
        double mr = mre[i*size+j], mi = mim[i*size+j];
        double vr = re[j],         vi = im[j];
        ar += mr*vr - mi*vi;
        ai += mr*vi + mi*vr;
    }
    outRe[i] = ar; outIm[i] = ai;
}
```

This removes 2× allocations per iteration, gives the JIT a tight primitive loop
(SLP auto-vectorisation; later trivially `ForkJoinPool`/Vector-API-able — that
parallelisation is Section 2's job, but the layout is provided here).

**Boundary handling (API stability):**

- `Computations.calculateNewState(List<Gate>, Complex[], int)` keeps its public
  signature; internally it unpacks to `StateVector`, runs the primitive path,
  repacks to `Complex[]` on return. So `SimpleQuantumExecutionEnvironment` and
  any external caller are unaffected.
- **Gate matrices:** flatten each gate's `Complex[][]` (from
  `gate.getMatrix()`, 512/538/558) into row-major `double[] mre, mim` once per
  apply (gate matrices are tiny). Keep `Gate.getMatrix()` returning
  `Complex[][]`.
- **`Gate.applyOptimize(Complex[] v)`** (507–509, 555) returns `Complex[]`.
  Respect it: unpack its result back into the SoA path. Optionally add an
  internal overload later; not required for correctness.
- **`processSwapGate` (469–477) and `permutateVector` (419–438)** are pure index
  permutations — port to operate on `re`/`im` index moves directly (cheap, no
  arithmetic).

**Migration order within WI-3:** (a) add `StateVector` + conversions with a
round-trip unit test; (b) convert the single-gate base case (550–567); (c)
convert the identity sub-block (498–527); (d) convert the recursive block
(528–549). Each sub-step is independently testable against the object path
(keep the old path behind a flag during bring-up, delete after parity).

### WI-4 — Amplitude pruning

**Files:** `local/Computations.java`, `local/SimpleQuantumExecutionEnvironment.java`,
new `SimulationOptions` (config carrier).

**Idea:** after each QuantumStep's state update, zero out amplitudes whose magnitude is
below a configurable threshold `ε`, optionally renormalise. This is an
*approximate* mode: it trades exactness for the ability to keep effectively
sparse states small and to skip negligible contributions.

**Config surface (new `SimulationOptions`):**

```java
public final class SimulationOptions {
    private double pruneThreshold = 0.0;   // 0 == disabled (exact, default)
    private boolean renormalizeAfterPrune = true;
    // getters/setters; passed to the QEE, not stored on Program
}
```

Default `pruneThreshold == 0.0` ⇒ **behaviour is byte-for-byte the exact path**;
pruning is strictly opt-in. Thread the options through
`SimpleQuantumExecutionEnvironment` (constructor or `runProgram` overload) into
the apply loop.

**Where applied:** once per QuantumStep, on the freshly computed state, in `applyStep`
(after line 161) — *not* inside the inner multiply-add (that would corrupt
partial sums). On the SoA buffers:

```java
if (opts.pruneThreshold() > 0) {
    double t2 = opts.pruneThreshold() * opts.pruneThreshold();
    double kept = 0;
    for (int i = 0; i < re.length; i++) {
        double p = re[i]*re[i] + im[i]*im[i];     // mirrors Complex.abssqr (170-172)
        if (p < t2) { re[i] = 0; im[i] = 0; }
        else kept += p;
    }
    if (opts.renormalize() && kept > 0) {
        double s = 1.0 / Math.sqrt(kept);
        for (int i = 0; i < re.length; i++) { re[i]*=s; im[i]*=s; }
    }
}
```

**Correctness / normalisation caveats (must be documented on the API):**

- Pruning is **non-unitary**: it discards probability mass `Σ_{pruned} |a_i|²`.
  Without renormalisation the state norm drops below 1, biasing subsequent
  measurement probabilities. With renormalisation, the surviving distribution is
  rescaled — measurement probabilities stay normalised but are *approximate*.
- **Error bound:** per QuantumStep the discarded probability is `< (dim_pruned)·ε²`,
  loosely bounded by `ε²·2^n`; total-variation error in the final distribution
  accumulates roughly additively over QuantumSteps. The Javadoc must state: pruning is
  only sound when the state is genuinely concentrated (low entanglement); for
  highly entangled states a small ε can still prune many small-but-collectively-
  significant amplitudes.
- Pruning **interacts with measurement reductions**
  (`calculateQubitStatesFromVector` 680–695, `doImmediateMeasurement` 707–730):
  those re-derive probabilities from the (now approximate) vector, so the error
  is inherited, not compounded by them.
- Expose the realised discarded mass per QuantumStep (e.g. via the existing
  `Result.setIntermediateProbability` channel or a new diagnostic) so users can
  see the approximation error they are accepting.

### WI-5 — Distributed state-vector simulation

**Files (new):** `distributed/` package —
`DistributedQuantumExecutionEnvironment` (implements
`QuantumExecutionEnvironment`, drop-in like the other backends per `ideas.md`
§6), `StatePartition`, transport interface `+ gRPC and/or RMI impls`.

**Partitioning model.** A state of `n` qubits has `2^n` amplitudes. Reserve the
top `k` qubits as *node-address* bits: split across `P = 2^k` nodes, each holding
a contiguous block of `2^{n-k}` amplitudes (local index = low `n-k` bits, node id
= high `k` bits). This is the standard amplitude-partitioning used by
distributed full-state simulators (e.g. qHiPSTER/Intel-QS style).

**Local vs. communicating gate operations** — derived directly from the bit a
gate acts on:

- **A gate on a "local" qubit** (target qubit index `< n-k`, i.e. below the
  partition boundary) touches only amplitude pairs that live within the same
  node. It runs *entirely locally* with no communication — this is exactly the
  per-partition inner loop already in `getNextProbability2`. Most of a circuit's
  gates can be arranged to be local.
- **A gate on a "global" qubit** (target index `≥ n-k`) pairs each amplitude with
  one whose node-address bit differs ⇒ the partner amplitude lives on another
  node. These require **point-to-point exchange** of half-blocks between paired
  nodes, then a local 2×2 (or 2^m×2^m) apply.
- **Pure permutations** (`processSwapGate` 469–477, `permutateVector` 419–438):
  - swap of two *local* qubits ⇒ local index shuffle, no comms;
  - swap touching a *global* qubit ⇒ either a paired block exchange or a
    logical qubit-relabel (renumber which qubit is "global") to avoid data
    movement — relabelling is the cheap trick and should be preferred.
- **Measurement / qubit-state reductions** (`calculateQubitStatesFromVector`
  680–695, `doImmediateMeasurement` 707–730) compute sums of `abssqr` over the
  whole vector ⇒ a **distributed all-reduce** (each node sums locally, then
  combine). `doImmediateMeasurement` also needs a single shared random draw and
  a post-selection rescale broadcast.
- **Pruning (WI-4)** composes cleanly: prune locally, all-reduce the kept mass,
  broadcast the scale factor, rescale locally.

**Reuse the SoA layout (WI-3):** each node holds a `StateVector` shard
(`double[] re/im` of size `2^{n-k}`). Block exchanges send raw primitive arrays —
no per-amplitude object serialisation. This is *the* reason WI-3 precedes WI-5.

**RMI vs gRPC tradeoff:**

| | Java RMI | gRPC |
|---|---|---|
| Setup | In-JDK, zero deps, trivial for JVM-only clusters | Protobuf + netty deps, codegen |
| Payload | Java serialization (slow, heavy) unless custom marshalling of `double[]` | Efficient binary; `bytes`/`repeated double` for shards |
| Cross-language | JVM-only | Polyglot (could pair with native/GPU workers) |
| Streaming / backpressure | None native | First-class (good for large block exchanges) |
| Ops/observability | Minimal | Mature (deadlines, interceptors, metrics) |

**Recommendation:** define a narrow internal `Transport` interface (methods:
`exchangeBlock(peer, double[] re, double[] im)`, `allReduceSum(double[])`,
`broadcast(...)`) and provide a **gRPC** implementation as the primary (efficient
primitive payloads, streaming for big shards, polyglot-friendly toward Section 2
GPU/native workers), with an **RMI** implementation as an optional zero-dependency
fallback for quick JVM-only clusters. Keep the math identical to the single-node
SoA path; only the block-exchange/all-reduce calls are new.

**Scope guardrail:** ship a *correct* gRPC two-/four-node implementation that
reproduces single-node results bit-for-bit (exact mode) before optimising comms.
This is genuinely complex; treat it as the last and largest item.

---

## 4. Testing Strategy

- **WI-1 (cache):** unit test that `getDecomposedSteps()` returns a non-null,
  stable list; that `addStep` after a read forces recomputation (assert
  identity changes / content reflects the new QuantumStep); that two reads without
  mutation return the same cached list. **Idempotence test:** decompose a
  program twice via the cache and assert the executed result is unchanged
  (guards the in-place `Step` mutation risk noted in WI-1).
- **WI-3 (SoA):** golden-master parity. For a battery of circuits (H/X/Y/Z,
  Rx/Ry/Rz, CNOT/CZ, Swap, Toffoli, QFT/IQFT, Grover, Shor `findPeriod`), run the
  existing object path and the new SoA path and assert amplitude-wise agreement
  within tolerance. Use `Complex.abssqr` (170–172) semantics for the metric.
  Provide `StateVector.fromComplex/toComplex` round-trip tests. (Use the
  proposed `QuantumAssert.assertStateEquals` from `ideas.md` §10 if/when it
  exists.)
- **WI-4 (pruning):** (a) with `pruneThreshold == 0`, assert *exact* equality to
  the pre-pruning path (no behavioural change when disabled); (b) error-bound
  test: on a known low-entanglement state, assert discarded mass `< ε²·dim` and
  that renormalised measurement probabilities stay within a derived tolerance;
  (c) adversarial test: a uniform-superposition state (e.g. H on all qubits)
  where every amplitude is `2^{-n/2}` — verify the API surfaces a large pruned
  mass / warning so users do not silently misuse pruning.
- **WI-5 (distributed):** P=2 and P=4 nodes (in-process transport stub for CI)
  must reproduce the single-node SoA result bit-for-bit in exact mode for
  local-only, global-qubit, swap, and measurement circuits. Separate
  integration test for the real gRPC transport.
- **Regression:** the whole existing test suite must pass unchanged after each
  WI (run with `rtk` per repo convention).

## 5. Benchmarks (JMH; cross-ref Section 10)

New `bench/` JMH module/profile (Section 10 "Benchmarking suite"). Benchmarks:

1. `ComplexRepBench` — the WI-2 matrix of representations, microbenchmarking the
   inner multiply-add and a full n-qubit gate apply; report ns/op, throughput,
   `-prof gc` allocation rate, and max-error vs double reference.
2. `StateVectorApplyBench` — end-to-end apply of representative gates (H, CNOT,
   QFT layer) at n ∈ {12,16,20,24}, object path vs SoA path.
3. `PruneBench` — apply-with-pruning throughput and realised error at several ε
   on a fixed circuit; show the larger-circuit reach enabled.
4. `DecompositionCacheBench` — cold (compute) vs warm (cached)
   `getDecomposedSteps()` on a deep program; confirms the lazy cache pays off.
5. `DistributedScalingBench` — wall time vs node count (1/2/4) and comms-bytes
   per QuantumStep for local-heavy vs global-heavy circuits.

Section 10's "scalability chart generator" (time vs n_qubits) consumes #2/#3/#5.

## 6. Risks

- **API stability (highest).** `Complex` public fields (`r`/`i`), constructors,
  and the singletons (`ZERO/ONE/I/HC/HCN`) are depended on externally and by
  internal identity comparisons (`tensor` 209, `permutate0` 295). Mitigation:
  do **not** change `Complex`'s public shape in WI-3; keep it as the boundary
  type and confine SoA to internal `Computations`/`StateVector`. Only revisit
  `Complex` itself (record/Valhalla) if WI-2 shows a decisive win *and* it can
  be done source-compatibly.
- **Numerical accuracy.** Current storage is `float` (69–70). The SoA migration
  should default to `double` to *improve* accuracy; if WI-2 favours `float` for
  bandwidth, document the precision change explicitly and gate it. Pruning
  introduces controlled, documented error (WI-4 caveats).
- **Decomposition idempotence.** `decomposeStep` mutates `Step` in place
  (`setComplexStep`, `addGate`, inserted permutation QuantumSteps). The lazy cache must
  guarantee decompose-once semantics; re-decomposing already-decomposed QuantumSteps is
  the main correctness hazard of WI-1.
- **Deprecation churn.** `setDecomposedSteps` (185–188) is public; remove only
  after a deprecation cycle. Same for the dead
  `SimpleQuantumExecutionEnvironment.decomposeSteps` helper (141–143) — internal,
  safe to delete now.
- **Distributed complexity & accuracy.** Cross-node `float`/`double` consistency
  and reduction order can change rounding; pin double precision and a fixed
  reduction order in exact mode to keep bit-for-bit parity with single node.
- **Valhalla availability.** Value classes depend on preview JDK features; keep
  that path behind a build profile so the main build stays on a stable JDK.

## 7. Suggested Sequencing

1. **WI-1** lazy decomposition cache — small, self-contained, removes a
   deprecated mutator from the hot loop. (No perf risk.)
2. **WI-2** representation benchmark — produces the data to justify WI-3's
   choice. Run before touching the hot path.
3. **WI-3** SoA refactor of `Computations` — the main single-node speedup;
   prerequisite layout for both Section 2 parallelism and WI-5.
4. **WI-4** amplitude pruning — builds on the SoA buffers from WI-3; opt-in,
   default-exact.
5. **WI-5** distributed simulation — last and largest; reuses SoA shards (WI-3),
   pruning all-reduce (WI-4), and the clean QuantumStep list (WI-1).
