# Plan 04 — Circuit-Level Abstractions

Section 4 of `ideas.md`: a fluent `Circuit` builder DSL, first-class `Subcircuit`s
(auto-inverse / auto-controlled), and a composable **transpiler pipeline**
(decompose → optimise → route → schedule) driven by a `DeviceTopology` connectivity
model. All work is additive and builds on the existing `Program`/`Step`/`Gate`/`Block`
model — no behavioural change to the simulator is required.

---

## 1. Overview & Goals

| Goal | Deliverable |
|------|-------------|
| Remove manual index arithmetic for circuit authoring | `Circuit` fluent builder → `Program` |
| First-class reusable subroutines | `Subcircuit` (wraps `Block`/`BlockGate`), named args, `inverse()`, `controlled(ctrl)` |
| Hardware-targeting compilation | `Transpiler` of composable `TranspilerPass`es: `Program → Program` |
| Connectivity-aware routing | `DeviceTopology` graph + `RoutePass` (SABRE-style SWAP insertion) |
| Gate-set retargeting | `DecomposePass` with KAK/Euler unitary decomposition |
| Correctness guarantee | Every pass is semantics-preserving up to global phase; verified by statevector equality |

**Non-goals**: no new simulator backend, no noise model (Section 5), no QASM I/O
(Section 6 — but the pass framework is designed so a QASM exporter can consume the
transpiled `Program`).

**Design invariant.** The unit of currency between every component is a Strange
`Program`. The DSL *produces* one, the transpiler *transforms* one, the simulator
*consumes* one. Nothing in this section invents a parallel IR; we annotate and rewrite
`Program`/`Step`/`Gate` objects only.

---

## 2. Current Model Analysis (real classes/methods)

Understanding the existing machinery is load-bearing because every pass must emit
objects the simulator already understands.

### 2.1 Program / QuantumStep / Gate

- `Program(int nQubits, QuantumStep... QuantumSteps)` holds an `ArrayList<Step> QuantumSteps`; `addStep`
  calls `ensureMeasuresafe` then `step.setIndex(steps.size())` and
  `step.setProgram(this)`. It caches `List<Step> decomposedSteps` (invalidated to
  `null` on every `addStep`). `getNumberQubits()`, `getSteps()`, `getInitialAlphas()`,
  `initializeQubit(idx, alpha)` are the public surface a builder must drive.
- `Step` holds `ArrayList<Gate> gates`; `addGate` calls `verifyUnique` which rejects
  two gates whose `getAffectedQubitIndexes()` overlap **within the same QuantumStep** — this
  is exactly the data-hazard check a scheduler needs (Section 8). `Step.Type`
  is `{NORMAL, PSEUDO, PROBABILITY}`. `setInverse(boolean)` reverses each gate, and
  for `BlockGate` it calls `((BlockGate)g).inverse()` rather than `setInverse`.
- `Gate` (interface) exposes the contract every transpiler output must satisfy:
  `getMatrix()` / `getMatrix(qee)`, `getAffectedQubitIndexes()`,
  `getHighestAffectedQubitIndex()`, `getMainQubitIndex()/setMainQubitIndex()`,
  `getSize()`, `setInverse(boolean)`, plus `hasOptimization()`/`applyOptimize(Complex[])`.
  Static factories already exist: `Gate.cnot`, `.cz`, `.hadamard`, `.x/.y/.z`,
  `.swap`, `.toffoli`, `.rotation(theta,axis,idx)`, `.rotationX/Y/Z`, `.measurement`,
  `.oracle`, `.permutation`. **The DSL is largely a fluent wrapper over these.**
- Gate family base classes establish identity for the optimiser:
  `SingleQubitGate` (idx, `getSize()==1`), `TwoQubitGate` (first/second,
  `getSecondQubitIndex()`, `getSize()==2`), `ThreeQubitGate`, `PermutationGate`
  (pure index swap, `getMatrix()` throws — handled specially), `BlockGate`.

### 2.2 How blocks / controlled blocks already work

- `Block(String name, int size)` holds `List<Step> QuantumSteps` and lazily builds a
  `Complex[][] matrix` of dimension `1<<nqubits`: it `decomposeStep`s each QuantumStep
  (`Computations.decomposeStep`), **reverses** the list (matrix-multiply order), then
  folds via `Complex.mmul` / `Complex.permutate`. `applyOptimize(probs, inverse)`
  applies the block to a state vector directly (used by the simulator hot path);
  when `inverse` it reverses QuantumSteps and flips `setInverse(true)` on each.
- `BlockGate<T>` *is a* `Gate` that places a `Block` at offset `idx`.
  `getAffectedQubitIndexes()` = `[idx, idx+block.getNQubits())`. `getMatrix(qee)`
  returns the block matrix, conjugate-transposed when `inverse`. `inverse()` flips the
  flag and returns `this`. `hasOptimization()==true` so the simulator calls
  `applyOptimize` instead of materialising the matrix. **This is the existing
  subcircuit substrate — `Subcircuit` extends it rather than replacing it.**
- `ControlledBlockGate<T> extends BlockGate` adds an `int control` qubit. Its key
  logic: `calculateHighLow()` validates that the control sits outside the block span,
  computes `low/high/size`, and (in `getMatrix`) builds the controlled matrix by
  embedding the block matrix in the lower-right quadrant of a `2*dim` identity
  (`Computations.createIdentity(2*dim)`), inserting `PermutationGate`s to bring the
  control adjacent. `applyOptimize` applies the block only to the half of the state
  where the control bit is set. `Computations.decomposeStep` already special-cases
  `ControlledBlockGate` via `processBlockGate(...)`, injecting the permutation QuantumSteps.
  **`controlled(ctrl)` therefore needs only to construct a `ControlledBlockGate` — the
  simulator already runs it.**

### 2.3 Decomposition / execution path

`SimpleQuantumExecutionEnvironment.runProgram` reads `p.getSteps()`, lazily builds
`simpleSteps` via `Computations.decomposeStep(step, nQubits)` (caching on the program),
then `applyStep` each. `decomposeStep` is the engine that lowers a multi-qubit gate on
non-adjacent qubits into `PermutationGate` + adjacent-gate + inverse-permutation QuantumSteps.
Matrix utilities live in `Complex`: `identityMatrix(dim)`, `tensor(a,b)`, `mmul(a,b)`,
`conjugateTranspose(src)`, `permutate(pg, matrix)`; and `Computations.createIdentity`,
`calculateStepMatrix`. **The transpiler reuses these for matrix-level reasoning; it does
not reimplement linear algebra.**

### 2.4 Gaps the plan must fill

1. No fluent authoring API — callers hand-build `Step`/`Gate` and index manually.
2. `Block`/`BlockGate` require **adjacent, contiguous** qubits at a fixed `idx`; there
   is no named-argument remapping, no nesting helper, no parameter binding.
3. No notion of a *basis gate set*, device coupling graph, or compilation passes.
4. `Program.getDecomposedSteps()` is `@Deprecated`; the transpiler should not rely on
   it and should produce an already-lowered `Program` where useful.

---

## 3. Detailed Work Items

New code lives in two new packages mirroring the flat layout:
`org.redfx.strange.circuit` (DSL + subcircuit) and `org.redfx.strange.transpile`
(passes + topology). Files are placed under `circuit/` and `transpile/` directories at
repo root, matching the existing `gate/`, `local/`, `algorithm/` convention.

### WI-1 — Circuit DSL  (`circuit/Circuit.java`)

A fluent, immutable-style builder that accumulates `Step`s and emits a `Program`.

**Design rule:** each fluent method appends **one new `Step`** containing one gate
(simple, predictable, always measure-safe ordering), *except* the explicit
`step(Gate...)` and `barrier()` escape hatches that pack a parallel QuantumStep. A later
`SchedulePass` (WI-8) re-packs single-gate QuantumSteps into parallel QuantumSteps; the DSL stays
dumb on purpose so semantics are obvious.

```java
package org.redfx.strange.circuit;

public final class Circuit {
    private final int nQubits;
    private final List<Step> QuantumSteps = new ArrayList<>();
    private final double[] initAlpha;          // mirrors Program.initAlpha

    private Circuit(int n) { this.nQubits = n; this.initAlpha = ... ; }

    /** Entry point: Circuit.of(5) */
    public static Circuit of(int nQubits) { return new Circuit(nQubits); }

    // --- single-qubit, map straight onto Gate factories / gate ctors ---
    public Circuit hadamard(int q)            { return add(new Hadamard(q)); }     // Gate.hadamard
    public Circuit h(int q)                   { return hadamard(q); }              // alias
    public Circuit x(int q)                   { return add(new X(q)); }
    public Circuit y(int q)                   { return add(new Y(q)); }
    public Circuit z(int q)                   { return add(new Z(q)); }
    public Circuit identity(int q)            { return add(new Identity(q)); }
    public Circuit rx(double theta, int q)    { return add(new RotationX(theta, q)); }
    public Circuit ry(double theta, int q)    { return add(new RotationY(theta, q)); }
    public Circuit rz(double theta, int q)    { return add(new RotationZ(theta, q)); }
    public Circuit r(double theta, Rotation.Axes a, int q) { return add(new Rotation(theta, a, q)); }

    // --- two-qubit ---
    public Circuit cnot(int ctrl, int target) { return add(new Cnot(ctrl, target)); }
    public Circuit cx(int c, int t)           { return cnot(c, t); }
    public Circuit cz(int a, int b)           { return add(new Cz(a, b)); }
    public Circuit cr(int c, int t, double th){ return add(new Cr(c, t, th)); }   // see Cr ctor
    public Circuit swap(int a, int b)         { return add(new Swap(a, b)); }

    // --- three-qubit ---
    public Circuit toffoli(int a, int b, int t){ return add(new Toffoli(a, b, t)); }

    // --- multi-qubit composite blocks ---
    public Circuit qft(int from, int to)      { return add(new Fourier(to - from + 1, from)); }
    public Circuit iqft(int from, int to)     { return add(new InvFourier(to - from + 1, from)); }
    public Circuit oracle(Complex[][] m)      { return add(new Oracle(m)); }

    // --- subcircuit application (WI-2) ---
    public Circuit apply(Subcircuit sub, int... qubitArgs);              // positional remap
    public Circuit apply(Subcircuit sub, Map<String,Integer> binding);  // named remap

    // --- measurement & structure ---
    public Circuit measure(int... qs);        // one Measurement gate per q, all in one QuantumStep
    public Circuit probability(int q)         { return add(new ProbabilitiesGate(q)); }
    public Circuit barrier();                  // PSEUDO QuantumStep; scheduling boundary
    public Circuit QuantumStep(Gate... parallel);     // explicit parallel QuantumStep (verifyUnique applies)
    public Circuit initQubit(int q, double alpha); // mirrors Program.initializeQubit

    // --- terminal ---
    public Program build();                    // constructs Program, addStep()s, applies initAlpha
    public Program buildTranspiled(Transpiler t); // build() then t.run(program)

    private Circuit add(Gate g) { QuantumSteps.add(new QuantumStep(g)); return this; }
}
```

**`build()` body:**

```java
public Program build() {
    Program p = new Program(nQubits);
    for (int q = 0; q < nQubits; q++) p.initializeQubit(q, initAlpha[q]);
    for (Step s : QuantumSteps) p.addStep(s);   // reuses ensureMeasuresafe + setIndex
    return p;
}
```

Notes:
- `measure(int...)` builds a single `Step` of `Measurement` gates; relies on
  `Step.verifyUnique` to reject duplicate indices and on `Program.ensureMeasuresafe`
  for measure-after-superposition safety — no new validation logic.
- The DSL never bypasses constructors; method-to-constructor mapping is 1:1 with the
  table above, so the optimiser/decomposer can recover gate identity by `instanceof`.
- `cr` maps to `gate/Cr.java` (verify its ctor signature `(control, target, theta)` at
  implementation time; adjust the wrapper accordingly).

### WI-2 — Subcircuit  (`circuit/Subcircuit.java`)

A first-class, **nestable** subroutine with **named qubit/parameter args**, built on
top of the existing `Block`. It does *not* fork the block/matrix machinery; it adds a
naming + remapping + composition layer and delegates execution to `BlockGate` /
`ControlledBlockGate`.

```java
package org.redfx.strange.circuit;

public final class Subcircuit {
    private final String name;
    private final List<String> qubitParams;          // e.g. ["a","b","c"]
    private final List<String> doubleParams;         // e.g. ["theta"]
    private final List<Instruction> body;            // gates referencing param NAMES
    private final boolean inverse;                   // accumulated

    // builder mirrors Circuit but addresses qubits by NAME
    public static Builder named(String name, String... qubitParams);
    // Builder.h("a"), .cnot("a","b"), .rx("theta","a"), .apply(other, "a","b"), .build()

    public int arity()                 { return qubitParams.size(); }

    /** Returns this subcircuit reversed and each instruction's gate inverted. */
    public Subcircuit inverse();

    /** Bind a concrete control qubit; instantiation yields a ControlledBlockGate. */
    public ControlledForm controlled(int controlArg /* index into qubitParams or wire */);

    /** Bind parameter values, leaving qubit args open. */
    public Subcircuit bind(String param, double value);

    // --- instantiation onto concrete wires ---
    /** positional: wire[i] is the absolute circuit index for qubitParams.get(i) */
    public BlockGate instantiate(int... wire);
    public BlockGate instantiate(Map<String,Integer> binding);
}
```

**How it maps to existing infrastructure**

1. **Block construction.** `instantiate` first computes the *contiguous span* the
   subcircuit needs. Subcircuit instructions are written against logical qubit indices
   `0..arity-1`. A `Block(name, arity)` is created and each instruction is added as a
   `Step` with gate indices already in `0..arity-1` space (so `Block.validateGate`
   passes). This is exactly how `Fourier` builds itself (`new Block(name, dim)`).
2. **Wire remapping.** The caller's `wire[]` need not be contiguous or sorted. Two cases:
   - **Contiguous & ascending** (`wire == [k, k+1, …, k+arity-1]`): return
     `new BlockGate(block, k)` directly — `BlockGate.getAffectedQubitIndexes()` already
     yields `[k, k+arity)`.
   - **Non-contiguous / permuted**: emit a *relabelling permutation envelope*. Build
     `PermutationGate` QuantumSteps (the same primitive `Computations.decomposeStep` and
     `ControlledBlockGate` use) that move `wire[i] → k+i`, place the `BlockGate`, then
     undo. In the DSL/transpiler this is realised as: pre-`Step`(perm) +
     `Step`(BlockGate at base `k`) + post-`Step`(perm). This reuses the **exact**
     mechanism `Computations.processBlockGate` already performs for controls, so no new
     simulator support is needed. (Implementation detail: compute `k = min(wire)` and a
     permutation sequence sorting `wire`.)
3. **`inverse()`** — reuse `BlockGate.inverse()` / `Block.applyOptimize(probs, true)`,
   which already reverses QuantumSteps and conjugate-transposes. `Subcircuit.inverse()` at the
   *definition* level reverses `body` and marks `inverse`; at instantiation it sets the
   resulting `BlockGate.inverse = true`. Matrix path: `BlockGate.getMatrix` already
   returns `Complex.conjugateTranspose(answer)` when `inverse`. **Auto-inverse is thus
   free** — we only expose it ergonomically.
4. **`controlled(ctrl)`** — return a form whose instantiation produces
   `new ControlledBlockGate(block, baseIdx, controlWire)`. `ControlledBlockGate` already
   implements the controlled matrix embedding and `applyOptimize` (apply block to the
   control-set half of the state). Validation (control must not lie inside the block
   span) is already enforced by `calculateHighLow()`. **Auto-controlled is therefore a
   thin constructor call.** Nested controls (`controlled(c1).controlled(c2)`) compose by
   wrapping: a `ControlledBlockGate` *is a* `BlockGate`, and its `getBlock()` can feed
   another `ControlledBlockGate(bg, idx, c2)` — leverage the existing
   `ControlledBlockGate(BlockGate bg, int idx, int control)` convenience ctor.
5. **Nesting / composition.** A subcircuit's `Builder.apply(other, "a","b")` records a
   nested-instantiation instruction. At `instantiate` time the inner subcircuit's body
   is *inlined* into the outer `Block` with remapped indices (flatten), OR added as a
   nested `BlockGate` QuantumStep inside the block (the simulator handles `BlockGate` inside a
   `Block` because `Block.getMatrix` / `applyOptimize` call `decomposeStep`/
   `calculateNewState`, both of which dispatch on `BlockGate`). Default: **inline** for
   matrix simplicity; keep nested-BlockGate as an option for large reusable blocks.

**`Instruction` internal model** (kept private to the package):

```java
sealed interface Instruction {
    record GateInsn(String factoryKey, List<String> qubitArgs, List<String> paramArgs) ...;
    record SubInsn(Subcircuit sub, List<String> qubitArgs, Map<String,String> paramBind) ...;
}
```

Resolution turns `factoryKey + bound indices` into a concrete `Gate` via the same
constructors WI-1 uses (share a single `GateFactory` switch).

### WI-3 — Transpiler pass interface  (`transpile/TranspilerPass.java`, `transpile/Transpiler.java`)

A pass is a pure `Program → Program` transform plus metadata; a `Transpiler` is an
ordered, named pipeline.

```java
package org.redfx.strange.transpile;

public interface TranspilerPass {
    /** Transform the program; MUST be semantics-preserving up to global phase
     *  unless it is a routing/decompose pass that records a qubit relabelling. */
    Program run(Program in, TranspileContext ctx);
    default String name() { return getClass().getSimpleName(); }
}
```

```java
public final class TranspileContext {
    private DeviceTopology topology;            // null = all-to-all
    private GateSet basisGates;                 // target basis (WI-4)
    private int[] logicalToPhysical;            // routing permutation (WI-7), identity by default
    private final Map<String,Object> attrs = new HashMap<>(); // pass scratch
    // getters/setters; immutable topology/basis, mutable mapping
}
```

```java
public final class Transpiler {
    private final List<TranspilerPass> passes;
    private Transpiler(List<TranspilerPass> p) { this.passes = p; }

    public static Transpiler of(TranspilerPass... passes) { ... }
    public static Transpiler standard(DeviceTopology topo, GateSet basis) {
        return of(new DecomposePass(basis),
                  new OptimisePass(),          // pre-route peephole
                  new RoutePass(topo),
                  new DecomposePass(basis),    // re-lower inserted SWAPs
                  new OptimisePass(),          // post-route cleanup
                  new SchedulePass());
    }

    public Program run(Program in) {
        TranspileContext ctx = new TranspileContext(...);
        Program cur = in;
        for (TranspilerPass p : passes) {
            cur = p.run(cur, ctx);
            assert TranspilerVerifier.equivalent(in, cur, ctx); // dev-mode guard, WI-Testing
        }
        return cur;
    }
}
```

Design choices:
- Passes communicate side-band data (e.g. final qubit mapping, gate-count deltas)
  through `TranspileContext`, **not** through the `Program` (which stays a clean IR).
- Every pass constructs a *new* `Program(nQubits)` and re-`addStep`s rewritten QuantumSteps;
  it never mutates the input in place, so the verifier can compare in↔out. (Note: a
  fresh `Program` re-runs `ensureMeasuresafe`; passes that legitimately reorder around
  measurements set QuantumSteps appropriately or operate pre-measurement.)
- Helper `ProgramRewriter` (new, `transpile/ProgramRewriter.java`) provides
  `mapEachGate(Program, Function<Gate,List<Gate>>)` and `mapSteps(...)` so individual
  passes stay short.

### WI-4 — GateSet + DecomposePass  (`transpile/GateSet.java`, `transpile/DecomposePass.java`)

**`GateSet`** declares the target basis and decides which gates are already legal.

```java
public final class GateSet {
    private final Set<Class<? extends Gate>> oneQ;   // e.g. {RotationZ.class, RotationX.class, ...}
    private final Set<Class<? extends Gate>> twoQ;    // e.g. {Cnot.class} or {Cz.class}
    public boolean isBasis(Gate g);                   // instanceof check over the sets
    public static GateSet CLIFFORD_T;                 // {H, S(=Rz(pi/2)), CNOT, T}
    public static GateSet IBM;                         // {Rz, SX, X, CNOT/ECR}
    public static GateSet UNIVERSAL_RZ_RX_CNOT;
}
```

**`DecomposePass`** lowers any gate not in the basis into basis gates. Strategy by gate
class (cite existing classes):

1. **Already basis** (`gateSet.isBasis(g)`): pass through unchanged.
2. **Known structural identities** (cheap, exact, table-driven):
   - `Toffoli` → 6×`Cnot` + `Hadamard` + `T`/`T†` (standard 15-gate Clifford+T). Pull
     the angle gates from `RotationZ` (T = Rz(π/4) up to phase) once S/T exist
     (Section 1); until then synthesise via `Rotation`.
   - `Swap` → 3×`Cnot` (already partly supported because `Computations.processSwapGate`
     handles `Swap`; for routing we keep `Swap` as a first-class gate and only decompose
     when the basis lacks it).
   - `Cz` ↔ `Cnot` conjugated by `Hadamard` on target.
   - `Fourier`/`InvFourier` (`BlockGate`s) → expand the block: each is already a `Block`
     of `Hadamard` + controlled-phase (`Cr`) + `Swap` QuantumSteps; emit those QuantumSteps with the
     block offset applied. Use `Block.getSteps()` to read them out, re-index by `+idx`.
3. **Arbitrary 1-qubit unitary** (any `SingleQubitGate` with a 2×2 `getMatrix()` not in
   basis): **ZYZ / Euler decomposition** → `Rz(α)·Ry(β)·Rz(γ)` (global phase dropped).
   New helper `Decompositions.euler(Complex[][] u)` returns `(alpha,beta,gamma,phase)`.
   Emits `RotationZ`, `RotationY`, `RotationZ` gates (or `Rz,Rx,Rz` if the basis prefers
   X). This makes *every* `SingleQubitMatrixGate` (`gate/SingleQubitMatrixGate.java`)
   retargetable.
4. **Arbitrary 2-qubit unitary** (any `TwoQubitGate`/2-qubit `BlockGate` with 4×4
   matrix): **KAK / Cartan decomposition** → at most 3 `Cnot`s + single-qubit rotations
   (`Decompositions.kak(Complex[][] u)`). This is the general fallback that guarantees
   universality. Reuse `Complex.mmul`, `conjugateTranspose` for the magic-basis algebra.
5. **n-qubit `BlockGate` (n≥3)** not otherwise known: materialise its matrix via
   `BlockGate.getMatrix()` and apply **Quantum Shannon Decomposition** recursively
   (cosine-sine decomposition → two multiplexed rotations + smaller blocks), bottoming
   out in KAK. This is design-level; v1 may instead expand the block's own
   `getSteps()` and recurse the decomposer on each sub-gate (cheaper, exact, and covers
   `Fourier`-like composites).

**Decomposition lives in** `transpile/Decompositions.java` (pure math over
`Complex[][]`), unit-tested independently against `getMatrix()` of the source gate
(reconstructed product must equal original up to global phase).

```java
public final class Decompositions {
    public record ZYZ(double alpha, double beta, double gamma, double phase) {}
    public static ZYZ euler(Complex[][] u2x2);
    public static List<Gate> kak(Complex[][] u4x4, int q0, int q1); // returns basis gates on q0,q1
    public static List<Gate> shannon(Complex[][] u, int baseIdx);   // n-qubit recursive
}
```

### WI-5 — OptimisePass  (`transpile/OptimisePass.java`)

A peephole/algebraic optimiser composed of independent **rewrite rules** so it is
extensible. Operates on the linear gate stream (one gate per logical QuantumStep after the DSL,
or per-step-flattened). Rules:

1. **Adjacent inverse cancellation.** Two consecutive gates on the same qubit(s) whose
   matrices multiply to identity (up to phase) cancel. Detect via gate identity first
   (`H·H`, `X·X`, `Cnot·Cnot`, `g` then `g.inverse()`), fall back to matrix check
   `Complex.mmul(a, b) ≈ identityMatrix`. Uses `getMatrix()` + `getAffectedQubitIndexes`.
2. **Rotation merging.** Consecutive same-axis rotations on one qubit merge:
   `Rz(a)·Rz(b) → Rz(a+b)`; drop if `a+b ≈ 0 (mod 2π)`. Detect by
   `instanceof RotationZ/RotationX/RotationY` and read the angle. (Requires a
   `getTheta()` accessor; `Rotation` stores `thetav` but does not expose it — add a
   getter as a tiny prerequisite, see Risks.)
3. **Commutation-based reordering.** If two gates commute (disjoint qubits, or
   diagonal gates sharing a control wire, e.g. `Rz`/`Cz` commute on the control),
   reorder to expose further cancellations. Commutation table keyed on gate class +
   shared-qubit role; conservative default = "commute only if qubit sets are disjoint".
4. **Peephole templates.** Small fixed rewrites: `H Z H → X`, `H X H → Z`,
   `Cnot(a,b) X(b) Cnot(a,b) → X(b)` etc., table-driven in `OptimiseRules`.
5. **(Stretch) ZX-calculus simplification.** A separate optional pass
   `transpile/ZXOptimisePass.java` translating the circuit to a ZX graph (spiders +
   Hadamard edges), running phase-gadget fusion + local complementation, and extracting
   back. Marked design-level/stretch; the four rules above cover the high-value cases
   without a graph rewriter. The pass interface (WI-3) makes it drop-in if added later.

Each rule implements:

```java
interface RewriteRule { Optional<List<Gate>> apply(List<Gate> window); int windowSize(); }
```

`OptimisePass` runs rules to a fixpoint (bounded iterations) over a sliding window.
Termination guard: stop when a full sweep produces no change or iteration cap hit.

### WI-6 — DeviceTopology  (`transpile/DeviceTopology.java`)

```java
public final class DeviceTopology {
    private final int nQubits;
    private final boolean[][] coupling;            // coupling[a][b] == directed edge a→b
    private final boolean symmetric;               // most sims/devices are bidirectional

    public DeviceTopology(int nQubits, int[][] edges, boolean symmetric);
    public boolean connected(int a, int b);
    public List<Integer> neighbours(int q);
    public int distance(int a, int b);             // BFS hop count, cached
    public int[][] distanceMatrix();               // all-pairs, BFS, cached

    // factory presets
    public static DeviceTopology linear(int n);    // 0-1-2-...-(n-1)
    public static DeviceTopology ring(int n);
    public static DeviceTopology grid(int rows, int cols);
    public static DeviceTopology heavyHex(int n);  // IBM-style
    public static DeviceTopology allToAll(int n);  // routing is a no-op
}
```

`distance`/`distanceMatrix` back the routing heuristic (WI-7). All-to-all topology
makes `RoutePass` a pass-through (early return), so non-hardware users pay nothing.

### WI-7 — RoutePass  (`transpile/RoutePass.java`)

Maps logical qubits to physical qubits respecting `DeviceTopology`, inserting `Swap`
gates so every 2-qubit gate acts on coupled physical qubits. Algorithm: **SABRE**
(SWAP-based Bidirectional heuristic search) — the standard, well-documented choice.

Pipeline inside the pass:

1. **Initial mapping.** Start with identity `logical→physical`, or a greedy mapping
   that places the most-interacting logical pairs on adjacent physical qubits (from a
   gate-interaction graph). Stored in `ctx.logicalToPhysical`.
2. **Front-layer traversal.** Walk QuantumSteps in order. Maintain a *front layer* of gates
   whose predecessors are resolved. For each gate:
   - 1-qubit gate: emit, remapping its index through the current mapping.
   - 2-qubit gate on physical `(p,q)`: if `topology.connected(p,q)`, emit; else it is
     blocked.
3. **SWAP selection.** When the front layer is blocked, score every candidate `Swap`
   on physical edges adjacent to a blocked gate's qubits by the heuristic
   `H = Σ_{g in front} distance(map(g.q0), map(g.q1))` (plus a decay/look-ahead term
   over the next layer). Apply the swap minimising `H`, update the mapping, record a
   `Swap` gate in the output `Program`. Repeat until the front layer can progress.
4. **Direction fix.** If the device coupling is directed and only `q→p` exists, wrap a
   `Cnot(p,q)` in `Hadamard`s on both qubits (`H H · Cnot(reverse) · H H`) — emit as a
   small decomposition (will be cleaned by the post-route `OptimisePass`).
5. **Output.** A new `Program` over the same qubit count whose 2-qubit gates are all
   hardware-legal, plus inserted `Swap`s; final mapping recorded in `ctx`. The inserted
   `Swap`s are real Strange `Swap` gates, already simulated by
   `Computations.processSwapGate`, so the routed program runs unchanged on the existing
   simulator.

`Swap`s are intentionally **not** pre-decomposed here; the second `DecomposePass`
(WI-3 `standard` pipeline) lowers them to `Cnot`s if the basis lacks `Swap`.

Helper: `RoutePass` builds a per-step dependency view using the same overlap test
`Step.verifyUnique` uses (qubit-set intersection) to know which gates are independent.

Steiner-tree routing for multi-controlled / parity networks is noted as a future
alternative pass (`SteinerRoutePass`) but SABRE is the v1 deliverable.

### WI-8 — SchedulePass  (`transpile/SchedulePass.java`)

Re-packs the (post-DSL, mostly one-gate-per-step) program into the **minimum number of
parallel `Step`s** by as-soon-as-possible (ASAP) list scheduling, parallelising gates
that touch disjoint qubits.

```java
public Program run(Program in, TranspileContext ctx) {
    int[] free = new int[in.getNumberQubits()]; // earliest free time-slot per qubit
    List<Step> slots = new ArrayList<>();        // one QuantumStep per time-slot
    for (Step s : in.getSteps())
      for (Gate g : s.getGates()) {
        List<Integer> qs = g.getAffectedQubitIndexes();
        int t = qs.stream().mapToInt(q -> free[q]).max().orElse(0); // earliest slot
        ensureSlot(slots, t).addGate(g);          // QuantumStep.verifyUnique guarantees no conflict
        for (int q : qs) free[q] = t + 1;
      }
    Program out = new Program(in.getNumberQubits());
    slots.forEach(out::addStep);
    return out;
}
```

- A `barrier()`/PSEUDO QuantumStep resets all `free[q]` to the barrier slot (hard scheduling
  boundary), preserving the user's intended ordering across barriers.
- Measurements pin to their slot and bump `free` so nothing reorders past a measure
  on the same wire (consistent with `Program.ensureMeasuresafe`).
- Output QuantumStep count = circuit depth; expose `ctx.attr("depth", slots.size())` for
  reporting. This is purely a regrouping — gate *order on each wire* is preserved, so it
  is trivially semantics-preserving.

---

## 4. Testing Strategy

The non-negotiable correctness criterion: **a transpiled circuit produces the same
statevector as the original**, up to a global phase and (for routing) a known qubit
permutation.

### 4.1 Semantic-equivalence harness  (`transpile/TranspilerVerifier.java` + tests)

```java
static boolean equivalent(Program a, Program b, TranspileContext ctx) {
    Complex[] va = statevector(a);                 // run on SimpleQuantumExecutionEnvironment
    Complex[] vb = statevector(b);
    vb = undoMapping(vb, ctx.logicalToPhysical);    // align routed qubit order
    return equalUpToGlobalPhase(va, vb, EPS);       // 1e-9
}
```

- `statevector(Program)` reuses the existing simulator path; pull the final amplitude
  vector. (Add a small accessor or run a `ProbabilitiesGate`-terminated copy; the
  simulator already exposes intermediate probability vectors via `Result`.)
- `equalUpToGlobalPhase`: find first index with non-negligible amplitude in both,
  compute the phase ratio, multiply one vector by it, compare element-wise within `EPS`.
- `undoMapping`: permute basis amplitudes by the inverse of `logicalToPhysical` using
  the same bit-swap logic as `Computations.permutateVector`/`swapBits`.

### 4.2 Per-component tests

- **DSL:** `Circuit.of(n)…build()` produces a `Program` whose `getSteps()`/gate types
  match a hand-built reference program; statevector identical. Test `measure`,
  `barrier`, `apply`, `initQubit`.
- **Subcircuit:** define a 2-qubit Bell subcircuit; `instantiate` on contiguous wires
  `[0,1]` and permuted wires `[1,0]` and a 3-qubit context at offset `[2,3]`; verify
  statevector vs an inline-built equivalent. Verify `inverse()` round-trips
  (`sub.then(sub.inverse())` ≈ identity) and `controlled(c)` matches a hand-built
  `ControlledBlockGate` (compare `getMatrix()` element-wise).
- **DecomposePass:** for each non-basis gate (`Toffoli`, `Swap`, `Cz`, random 1-qubit
  `SingleQubitMatrixGate`, random 2-qubit unitary), assert the decomposed gate list's
  product matrix (via `Complex.mmul`) equals the original `getMatrix()` up to phase, and
  that the whole program's statevector is preserved.
- **OptimisePass:** assert gate-count strictly decreases on crafted redundant circuits
  (`H H`, `Rz(a) Rz(-a)`, `X X`) and statevector is unchanged; assert idempotence
  (running twice == running once).
- **DeviceTopology:** unit-test `distance`/`neighbours`/`distanceMatrix` on
  linear/ring/grid presets against known values.
- **RoutePass:** on a `linear(n)` topology with a circuit full of long-range `Cnot`s,
  assert every output 2-qubit gate is `topology.connected(...)`, and statevector matches
  after `undoMapping`. Include an all-to-all case asserting zero SWAPs inserted.
- **SchedulePass:** assert output depth ≤ input QuantumStep count, every `Step` passes
  `verifyUnique`, and statevector unchanged.

### 4.3 Property-based fuzzing

Generate random valid `Program`s (random gates on random qubits, sizes 1–5), run the
full `Transpiler.standard(topo, basis)`, and assert `TranspilerVerifier.equivalent`.
This catches edge cases in decompose/route interaction. Cap qubit count at ~8 to keep
the dense simulator fast.

---

## 5. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| **Global-phase drift** accumulates across passes and breaks naive equality | Always compare *up to global phase*; never assert raw amplitude equality. |
| `Rotation` does not expose its angle (`thetav` is private) — blocks rotation-merge | Tiny prerequisite: add `getTheta()`/`getAxis()` getters to `Rotation` (additive, safe). Flag early. |
| `BlockGate`/`Block` assume **contiguous, ascending** qubits at fixed `idx`; non-contiguous subcircuit args | Use the permutation-envelope pattern already proven by `ControlledBlockGate`/`processBlockGate`; never feed `Block` non-contiguous indices directly. |
| `ControlledBlockGate.getMatrix` logs `"Matrix was cached"` and has subtle gap/permutation cases | Treat `controlled()` as delegating to existing behaviour; add focused tests at construction; do **not** reimplement its matrix logic. |
| KAK / Shannon decomposition numerically fragile | Isolate in `Decompositions` with standalone matrix-reconstruction tests at `1e-9`; gate the n-qubit Shannon path behind the cheaper "expand `getSteps()` and recurse" fallback for v1. |
| Routing changes qubit identity; verifier must account for the mapping | Thread `logicalToPhysical` through `TranspileContext`; `undoMapping` in the verifier. |
| Fresh-`Program` rebuild re-triggers `ensureMeasuresafe`, which only knows `Hadamard`/`Cnot` superposition sources | Passes operate on the pre-measurement region or preserve measure-step positions; document that `ensureMeasuresafe` is a conservative check, not a transpiler concern. |
| `@Deprecated getDecomposedSteps` coupling | Transpiler never reads/writes it; relies on `Computations.decomposeStep` directly when matrices are needed. |
| Performance: matrix-based equivalence checks on large circuits | Bound test qubit counts (≤8); for production transpile, passes are matrix-free except `DecomposePass` on genuinely opaque unitaries. |

---

## 6. Suggested Sequencing

1. **WI-1 Circuit DSL** + `build()` — immediately useful, zero dependencies, unblocks
   ergonomic test authoring for everything else.
2. **WI-6 DeviceTopology** — pure data structure, no dependencies, needed by routing
   and testable in isolation.
3. **WI-3 Transpiler/TranspilerPass/Context** + `ProgramRewriter` + **TranspilerVerifier**
   (testing harness first, so every later pass is verified on arrival).
4. **WI-8 SchedulePass** — simplest semantics-preserving pass; validates the framework
   end-to-end.
5. **WI-5 OptimisePass** (rules 1–4; prerequisite: `Rotation.getTheta()`).
6. **WI-2 Subcircuit** — builds on `Block`/`BlockGate`/`ControlledBlockGate`; needs the
   DSL and benefits from the verifier.
7. **WI-4 GateSet + DecomposePass** with `Decompositions` (Euler first, then KAK, then
   Shannon/stretch).
8. **WI-7 RoutePass** (SABRE) — depends on DeviceTopology + decompose (for SWAP/dir fix)
   + scheduler.
9. **Stretch:** `ZXOptimisePass`, `SteinerRoutePass`, n-qubit Shannon — slot in as new
   `TranspilerPass`es without touching the pipeline contract.

Throughout: each pass merged only when its per-component tests **and** the
property-based equivalence fuzzer pass.
