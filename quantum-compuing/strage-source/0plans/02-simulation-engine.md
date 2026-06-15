# Plan 02 — Simulation Engine Improvements

Scope: Section 2 of `ideas.md` ("Simulation Engine Improvements"). This plan
adds alternative simulation backends and accelerates the existing dense
state-vector engine, all behind the existing
`org.redfx.strange.QuantumExecutionEnvironment` interface so each is a drop-in
replacement for `SimpleQuantumExecutionEnvironment`.

Backends covered:

1. Sparse state-vector simulator (skip zero amplitudes)
2. Parallel CPU dense simulator (ForkJoinPool / structured concurrency)
3. Stabilizer (Clifford) simulator (Aaronson–Gottesman tableau)
4. Matrix Product State (MPS) simulator (50+ qubits, shallow circuits)
5. Decision-diagram (DD) simulator
6. GPU acceleration (OpenCL; Panama FFI to cuBLAS/Metal)
7. Approximate simulation: amplitude pruning
8. Complex-number hot-path optimization (shared enabler)

---

## 1. Overview & Goals

The current engine, `org.redfx.strange.local.SimpleQuantumExecutionEnvironment`,
holds the full `Complex[]` state vector of length `1 << nQubits` and applies
each `Step` by dense matrix–vector multiplication in
`org.redfx.strange.local.Computations`. This is O(2^n) memory and, per gate,
O(4^k · 2^(n-k)) arithmetic (k = gate width). It runs out of heap around 25–28
qubits and is single-threaded.

Goals:

- Keep the public surface identical: every new backend is a class implementing
  `QuantumExecutionEnvironment`, instantiated by the user instead of
  `new SimpleQuantumExecutionEnvironment()`. No change to `Program`, `Step`,
  `Gate`, `Result`, or any gate class is required for a backend to be usable.
- Reuse the existing decomposition pipeline (`Computations.decomposeStep`,
  `Program.getDecomposedSteps`/`setDecomposedSteps`) wherever a backend wants
  the same "no permutations, adjacent qubits" normal form the dense engine uses.
- Backends that cannot represent a given circuit (e.g. stabilizer sim fed a
  `Rotation` of arbitrary angle, MPS fed a dense `Oracle`) must fail fast or
  fall back to dense, never silently return wrong results.
- Cross-validate every backend against `SimpleQuantumExecutionEnvironment` on
  small circuits (≤ 12 qubits) with amplitude/probability tolerance checks.

Non-goals here: noise/density-matrix simulation (Section 5 of `ideas.md`),
hardware backends (Section 6). Amplitude pruning is included because it is a
pure-simulation approximation, but the broader noise model is out of scope.

---

## 2. Current Architecture Analysis (cited methods)

Understanding the exact contract a backend must honor.

### 2.1 Entry point and state initialization

`SimpleQuantumExecutionEnvironment.runProgram(Program p)`:

- `int nQubits = p.getNumberQubits(); int dim = 1 << nQubits;`
- Reads `double[] initalpha = p.getInitialAlphas()` and builds the initial
  `Complex[dim] probs` as the tensor product of per-qubit single-qubit states:
  for basis index `i`, bit `j` (MSB-first: `pw = nQubits - j - 1`), it multiplies
  in `initalpha[j]` when the bit is 0 and `sqrt(1 - alpha^2)` when the bit is 1.
  Any backend that does not start from |0…0⟩ must replicate this amplitude rule.
- Pulls `p.getSteps()` and the cached `p.getDecomposedSteps()`. If the cache is
  null it builds `simpleSteps` by `Computations.decomposeStep(step, nQubits)`
  per step and stores it via `p.setDecomposedSteps(simpleSteps)`.
- Creates `Result result = new Result(nQubits, steps.size())` and calls
  `result.setIntermediateProbability(0, probs)` for the initial snapshot.
- Loops decomposed steps; for each non-empty step calls `applyStep`, then if
  `step.getComplexStep() > -1` records `result.setIntermediateProbability(idx, probs)`.
- After the loop: `calculateQubitStatesFromVector(probs)` → per-qubit
  probabilities, set via `qubit[i].setProbability(...)`; then
  `result.measureSystem()`, `p.setResult(result)`, return.

`runProgram(Program p, Consumer<Result> result)` simply spawns a `Thread` that
calls the synchronous variant and feeds the consumer. Every backend overrides
both.

`mmul(Complex[][] a, Complex[][] b)` is a `default` interface method delegating
to `Complex.mmul`; backends inherit it unchanged.

### 2.2 Step decomposition (reusable by all backends)

`Computations.decomposeStep(Step s, int nqubit)` rewrites a `Step` into a list
of steps that the dense engine can run without arbitrary permutations:

- `Step.Type.PSEUDO` steps pass through (sets `complexStep = index`).
- A step whose gates are all `SingleQubitGate` passes through unchanged
  (`simple` branch).
- A single `Oracle` or `Swap` passes through.
- For `TwoQubitGate`/`ThreeQubitGate` whose operands are not already adjacent and
  descending, it inserts `PermutationGate` pre/post steps (via `new Step(pg)`),
  juggling `setComplexStep`/`getIndex` so the "real" matrix step is tagged and
  the wrapping permutations are tagged `-1`.
- `ControlledBlockGate` is expanded through `processBlockGate`.

The important invariant for backends that reuse this: after decomposition, every
matrix-bearing step has its operand qubits adjacent and in descending index
order, and permutations are isolated into their own steps. The
`getComplexStep()` value identifies which physical step a decomposed step
corresponds to, for intermediate-probability snapshots.

### 2.3 The dense hot path

`applyStep` dispatches three special cases before the general path:

- First gate is `ProbabilitiesGate` → record amplitudes via
  `probGate.setProbabilites(vector)` and return the vector unchanged.
- Single `PermutationGate` → `Computations.permutateVector(vector, idx1, idx2)`.
- Otherwise → `Computations.calculateNewState(gates, vector, nQubits)`.

`Computations.calculateNewState(List<Gate> gates, Complex[] vector, int length)`:

- If `containsImmediateMeasurementOnly(gates)` → `doImmediateMeasurement` (random
  collapse on one qubit, renormalize, optional `Consumer<Boolean>` callback).
- Else → `getNextProbability(getAllGates(gates, length), vector)`.

`getAllGates(gates, length)` pads the gate list to a full per-qubit list from
qubit `length-1` down to `0`, filling gaps with `new Identity(idx)` and skipping
indices consumed by multi-qubit gates (using `getHighestAffectedQubitIndex`,
`getSize`). This is the per-step gate column.

`getNextProbability` / `getNextProbability2` is the **core matrix–vector
multiply** and the primary target for parallelization and GPU offload:

- Recursive tensor-structured application. `gate = gates.get(0)` is the
  highest-index gate; `gatedim = 1 << gate.getSize()`; `partdim = size / gatedim`.
- Special-cases a leading `Swap` with otherwise-identity tail
  (`processSwapGate`, pure index permutation).
- "Only identity tail" branch: for each of `partdim` strides `j`, gathers
  `oldv[i] = v[i*partdim + j]`, applies either `gate.applyOptimize(oldv)` (when
  `gate.hasOptimization()`) or the dense `gatedim×gatedim` matrix multiply
  `newv[i] += matrix[i][k] * oldv[k]`, scatters back to `answer[i*partdim + j]`.
- General branch: recurses on `nextGates` to build `vsub[gatedim][partdim]`, then
  contracts the leading gate's matrix against it:
  `answer[j + i*partdim] += matrix[i][k] * vsub[k][j]`.
- Base case (`gates.size()==1`): either `gate.applyOptimize(v)` or a full
  `size×size` dense multiply.

All arithmetic uses `Complex`.

### 2.4 Complex representation (hot-path cost)

`Complex` is **not** a record. It is a final class with
mutable `public float r; public float i;` (note: single precision). Constants
`ZERO`, `ONE`, `I`, `HC`, `HCN`. Methods used in the hot loop:

- `add(Complex)` and `mul(Complex)` each **allocate a new `Complex`**.
- In-place variants exist but are unused by the hot path: `addr`, `addmulr`
  (fused multiply-add into `this`), `mul(double)`.
- `abssqr()` returns `r*r + i*i` (used for probabilities/measurement).
- `tensor`, `mmul`/`slowmmul`, `conjugateTranspose`, `permutate*` are matrix
  helpers.

Consequences for the hot path: the inner `answer[i] = answer[i].add(matrix[i][j].mul(v[j]))`
allocates two `Complex` objects per scalar multiply-add, producing O(4^k · 2^(n-k))
short-lived objects per step. This dominates GC and cache behavior and is the
single biggest dense-engine performance problem. The pre-existing `addmulr`
fused method is the in-place primitive that fixes it but is currently unused.

---

## 3. Shared Enabler — Complex hot-path optimization (do first)

This is a prerequisite for the parallel and GPU work and improves the existing
dense engine immediately. It introduces a primitive-array state representation
used by the new backends, while leaving `Complex[]` working for compatibility.

### 3.1 Work items

- **W3.1 `Complex[]` → `double[]` interleaved buffers.** Add a package-private
  utility in `org.redfx.strange.local` (new file `local/StateVector.java`) that
  converts between `Complex[] probs` and an interleaved `double[2*dim]`
  (`re=buf[2i]`, `im=buf[2i+1]`), plus the reverse for snapshotting into
  `Result.setIntermediateProbability`. This avoids touching `Complex` semantics
  while removing per-element boxing in the inner loops.
  - Rationale matches `ideas.md` §13 ("benchmark `Complex` vs primitive
    `double[]` arrays vs value types").
- **W3.2 Allocation-free inner loop (`Complex[]` path).** Provide an alternate
  matrix–vector kernel in `Computations` (new method, e.g.
  `getNextProbabilityInPlace`) that pre-allocates `answer[i] = new Complex(0,0)`
  once and uses the existing `Complex.addmulr(a, b)` fused multiply-add instead
  of `answer[i].add(matrix[i][j].mul(v[j]))`. This eliminates the two-object
  allocation per scalar op without changing results. Gate behind a static flag
  so the original path remains for cross-checking.
- **W3.3 Precision note.** `Complex` stores `float` (single precision).
  `double[]` kernels in W3.1 widen to double internally; document that new
  backends carry double precision while `Complex`-based snapshots round back to
  float on the boundary. Add a test asserting the dense engine result is
  unchanged within `1e-4` (float tolerance) after W3.2.

Integration point: `applyStep` calls `calculateNewState`; the new kernel is
selected there or inside `getNextProbability2`. No interface change.

Files: `local/StateVector.java` (new), `local/Computations.java` (add kernels,
do not remove existing ones).

---

## 4. Backend 1 — Sparse state-vector simulator

For low-entanglement / permutation-heavy circuits (Grover oracles, arithmetic in
`gate/Add*`, `MulModulus`) most amplitudes are zero; storing only nonzeros is a
large win.

### 4.1 Class & integration

- New file `local/SparseQuantumExecutionEnvironment.java` implementing
  `QuantumExecutionEnvironment`.
- Mirrors `SimpleQuantumExecutionEnvironment.runProgram` structure: same
  `nQubits`/`dim`, same `getInitialAlphas` initialization (but only inserts
  nonzero basis amplitudes), same use of `decomposeStep`/`getDecomposedSteps`
  cache, same intermediate-probability snapshots keyed by `getComplexStep()`.
- At snapshot/measurement boundaries, densify into a `Complex[dim]` (zero-filled)
  to satisfy `result.setIntermediateProbability` and
  `calculateQubitStatesFromVector`. Final `measureSystem()` unchanged.

### 4.2 Data structure

- `Long2ObjectOpenHashMap<double[2]>` style map keyed by basis index (a
  `long` to allow >31 qubits later), value = interleaved `{re, im}`. Use a plain
  `HashMap<Long, double[]>` first; swap for a primitive map (fastutil or a custom
  open-addressing `long→complex` map) once correct.
- Threshold ε: drop entries with magnitude² < ε after each gate (ties into the
  amplitude-pruning backend, §10).

### 4.3 Gate application (sparse kernels)

Rather than build the per-step gate column via `getAllGates`, apply gates
directly to the sparse map:

- **SingleQubitGate at qubit q**: for each occupied index `i`, the 2×2 matrix
  couples `i` to `i ^ (1<<q)`. Process pairs once (when bit q of `i` is 0),
  compute the two output amplitudes, write back. `Identity` skipped entirely.
  `applyOptimize`/`hasOptimization` honored where present (X is a pure key
  flip `i ^ (1<<q)`; Z/phase a scalar; H mixes a pair).
- **TwoQubitGate (`Cnot`, `Cz`, `Swap`, `Cr`)**: handle as index/phase ops where
  possible — `Cnot(c,t)` flips bit t when bit c is set (pure key remap), `Cz`
  applies −1 phase, `Swap` swaps bits. General 4×4 `getMatrix()` couples the
  group of up-to-4 basis indices sharing the non-operand bits.
- **`PermutationGate`**: pure key remap (`swapBits`), reusing the bit logic from
  `Computations.permutateVector`/`Computations.swapBits`.
- **`Oracle` / dense `BlockGate`**: cannot be applied sparsely in general; densify
  the affected register, multiply, re-sparsify. If the whole circuit hits this
  often, log and recommend the dense engine.
- **Measurement / `ImmediateMeasurement`**: sum `abssqr` over the relevant bit,
  pick, filter the map, renormalize — same semantics as
  `Computations.doImmediateMeasurement`.

### 4.4 Sequencing within backend

Single-qubit + CNOT/CZ/Swap/Permutation first (covers Grover, QFT, teleportation),
then dense-register fallback for Oracle/BlockGate, then primitive-map swap-in.

---

## 5. Backend 2 — Parallel CPU dense simulator

Directly addresses `ideas.md` §2 ("`Computations.calculateNewState` is
single-threaded"). Same results as the dense engine, faster on multicore.

### 5.1 Class & integration

- New file `local/ParallelQuantumExecutionEnvironment.java` extending or
  delegating to `SimpleQuantumExecutionEnvironment`. It overrides nothing in the
  control flow; it only swaps the inner kernel called from `applyStep` →
  `Computations.calculateNewState`.
- Cleanest approach: extract the kernel selection in `getNextProbability2` so a
  parallel variant can be plugged. Add `Computations.calculateNewStateParallel`
  using a shared `ForkJoinPool` (or `Executors.newVirtualThreadPerTaskExecutor`
  on Java 21+ for structured concurrency).

### 5.2 Parallelization mapping onto the existing loops

The matrix–vector multiply has two embarrassingly parallel axes:

- **"Only identity tail" branch**: the outer loop `for (j = 0; j < partdim; j++)`
  over independent strides is the parallel unit. Each stride gathers
  `oldv`, applies the gate, scatters to `answer[i*partdim + j]` — disjoint writes,
  no synchronization. Parallelize with a `ForkJoinPool` `RecursiveAction`
  splitting the `[0, partdim)` range, threshold ~1<<12 to bound task overhead.
- **General branch**: parallelize the outer `for (i = 0; i < gatedim; i++)` and/or
  `for (j = 0; j < partdim; j++)` writing disjoint `answer[j + i*partdim]`. The
  recursion building `vsub[i]` (`for i in gatedim`) is also independent and can be
  forked.
- **Base-case full multiply** (`for i in size`): split `[0,size)` rows across
  tasks; each computes `answer[i]` independently.

Use the `double[]` interleaved buffers from W3.1 to avoid `Complex` allocation
contention across threads (allocation churn is worse under parallelism). Writes
are to disjoint indices so no locks are needed; a single barrier (pool `invoke`)
joins per step.

### 5.3 Constraints

- `processSwapGate`, `permutateVector` are memory-bound index shuffles —
  parallelize only above a size threshold.
- Thread-count and threshold configurable via constructor
  (`ParallelQuantumExecutionEnvironment(int parallelism)`).

---

## 6. Backend 3 — Stabilizer (Clifford) simulator

Aaronson–Gottesman tableau. Poly-time and poly-memory for Clifford-only
circuits (H, S, X, Y, Z, CNOT, CZ, Swap, measurement). Required for
error-correction research (`ideas.md` §3 QEC codes).

### 6.1 Class & integration

- New file `local/StabilizerQuantumExecutionEnvironment.java` implementing
  `QuantumExecutionEnvironment`.
- `runProgram`: build tableau for `nQubits` from |0…0⟩ (valid because
  `getInitialAlphas` for the standard case is all-zero ⇒ |0⟩; if any alpha is not
  exactly 0 or 1, the state is non-stabilizer → throw/fallback).
- Iterate `p.getSteps()` (the stabilizer formalism does **not** need
  `decomposeStep`'s permutation expansion — Clifford updates act on arbitrary
  qubit pairs directly). Still honor `getComplexStep`-style snapshots by
  optionally densifying the stabilizer state to a `Complex[]` for
  `setIntermediateProbability` (only feasible for small n; otherwise record
  probabilities by sampling).
- Non-Clifford gate encountered (any `Rotation`/`RotationX/Y/Z`/`Cr` with angle
  not a multiple of π/2, `Toffoli`, `Oracle`, arithmetic gates) ⇒ throw
  `UnsupportedOperationException` with a clear message, or fall back to dense if a
  fallback flag is set. Detect via gate class + parameter inspection.

### 6.2 Tableau data structure

For n qubits, a binary matrix of `2n` rows (n destabilizers, n stabilizers) by
`2n+1` columns: `x` bits `[0,n)`, `z` bits `[n,2n)`, and a phase bit `r` per row.
Store as `long[]` bitsets per row (or `boolean[][]`) — O(n²) bits total.

### 6.3 Per-gate tableau update rules (act on all 2n rows)

For each row with `x_q`, `z_q` (bits at the gate's qubit(s)) and phase `r`:

- **Hadamard(q)**: `r ^= x_q & z_q`; swap `x_q ↔ z_q`.
- **Phase S(q)**: `r ^= x_q & z_q`; `z_q ^= x_q`.
- **CNOT(c,t)**: `r ^= x_c & z_t & (x_t ^ z_c ^ 1)`; `x_t ^= x_c`; `z_c ^= z_t`.
- **X(q)** = H·S²·H equivalently `r ^= z_q`.
- **Z(q)**: `r ^= x_q`. **Y(q)**: `r ^= x_q ^ z_q`.
- **CZ(c,t)** = H(t)·CNOT(c,t)·H(t); implement directly:
  `r ^= x_c & x_t & (z_c ^ z_t)`; `z_t ^= x_c`; `z_c ^= x_t`.
- **Swap(a,b)**: swap columns (`x_a↔x_b`, `z_a↔z_b`).
- **Measurement(q)**: standard Aaronson–Gottesman measure — if any stabilizer row
  has `x_q=1` (random outcome): pick such row p, rowsum others onto it, replace
  destabilizer p with old stabilizer p, set stabilizer p to a deterministic Z_q
  with random phase. Else (deterministic): use scratch row + rowsum to read the
  sign. Reuse `swapBits` only for the Swap case; the rest is bit arithmetic with
  the row-sum helper (which tracks the i-phase exponent g per the AG paper).

### 6.4 Output

- `Result` qubit probabilities from deterministic/random measurement outcomes; for
  full-distribution snapshots on small n, densify via the canonical
  stabilizer-state extraction (Gaussian elimination on the stabilizer group → one
  basis state, then build superposition). Document the n limit for densification.

---

## 7. Backend 4 — Matrix Product State (MPS) simulator

Scales to 50+ qubits for shallow / low-entanglement circuits; truncates bond
dimension χ for approximate simulation otherwise.

### 7.1 Class & integration

- New file `local/MPSQuantumExecutionEnvironment.java` implementing
  `QuantumExecutionEnvironment`.
- `runProgram`: initialize a product-state MPS for `nQubits` from
  `getInitialAlphas` (each site tensor is the 1×2×1 single-qubit state). Iterate
  `p.getSteps()`. Reuse `decomposeStep` is optional; MPS prefers gates on
  **adjacent** sites, and `decomposeStep` already inserts `PermutationGate`s to
  make multi-qubit operands adjacent — so reusing it gives MPS a stream of
  single-site gates and adjacent two-site gates, exactly MPS's sweet spot. Apply
  `PermutationGate`/`Swap` as physical site swaps in the chain.
- Snapshots: contract the MPS to a `Complex[]` only when small enough; otherwise
  record marginal per-qubit probabilities (computable cheaply from the MPS).

### 7.2 Data structures

- Chain of site tensors `A[k]` of shape `(χ_left, 2, χ_right)` stored as
  `double[]` interleaved complex. Bond dimensions `χ_k` bounded by configurable
  `maxBond` (χ). `λ[k]` Schmidt-coefficient vectors at bonds (canonical form).

### 7.3 Gate application

- **Single-site gate (q)**: contract the 2×2 `getMatrix()` into site tensor
  `A[q]`'s physical index. O(χ²) — no bond growth.
- **Adjacent two-site gate (q, q+1)**: contract both site tensors and the 4×4
  gate into a single (χ_left, 2, 2, χ_right) tensor, reshape to a matrix, **SVD**,
  truncate to `maxBond` keeping the χ largest singular values, split back into two
  site tensors and update the bond λ. Bond dimension growth and truncation error
  live here. Need a complex SVD — pull in a small dependency or implement via
  Jacobi/Golub–Kahan on the interleaved buffers. Truncated weight = approximation
  error; expose it.
- **Non-adjacent multi-qubit gate**: insert `Swap`s to bring sites adjacent
  (decompose pipeline already does much of this), apply, swap back.
- **Oracle / wide BlockGate**: not MPS-friendly; throw or densify a local window.
- **Measurement**: sample from the MPS by sweeping and conditioning site tensors;
  renormalize. Per-qubit marginals from local reduced density matrices.

### 7.4 Parameters

- Constructor `MPSQuantumExecutionEnvironment(int maxBond, double truncTol)`.
  `maxBond = Integer.MAX_VALUE` ⇒ exact (memory permitting). Report cumulative
  truncation error on `Result` (extend via a side-channel, not the interface).

---

## 8. Backend 5 — Decision-diagram (DD) simulator

QMDD-style (à la JKU DDSIM). Compact for states/operators with structural
redundancy.

### 8.1 Class & integration

- New file `local/DDQuantumExecutionEnvironment.java` implementing
  `QuantumExecutionEnvironment`. Same `runProgram` skeleton; reuse
  `getInitialAlphas` to build the initial vector-DD, reuse `decomposeStep` so
  gates arrive in normal form, snapshot by enumerating the DD to `Complex[]` when
  small.

### 8.2 Data structures

- Vector DD: nodes with two successor edges (qubit = 0 / = 1), each edge carrying
  a complex weight; shared subgraphs via a unique table (hash-cons). Complex
  weights interned in a weight table with tolerance ε for normalization.
- Operator DD optional (for building gate matrices as DDs); start with applying
  gate matrices node-wise to the vector DD.

### 8.3 Gate application

- Apply each per-qubit gate (from `getAllGates`-style column or directly per gate)
  by the standard DD `apply`/`multiply` recursion with memoization (compute
  table). Reductions (normalize, merge identical children) after each op.
- X/Z/Swap/CNOT have especially compact DD updates (edge rewiring + weight
  factors). Oracle/dense gates: convert to operator DD or densify a window.

### 8.4 Notes

DD is the most complex backend; sequence it last. Correctness is validated the
same way (densify small circuits, compare to dense engine).

---

## 9. Backend 6 — GPU acceleration

Offload the `getNextProbability2` matrix–vector multiply (the dense kernel) to
GPU. This is an accelerator for the dense path, not a new representation.

### 9.1 Integration

- New file `local/GpuQuantumExecutionEnvironment.java` implementing
  `QuantumExecutionEnvironment`, structured like `SimpleQuantumExecutionEnvironment`
  but routing the per-step kernel to a GPU backend. State lives on-device as a
  `double[]`/`float[]` interleaved buffer (reuse W3.1 layout); copy in once at
  start, snapshot back to `Complex[]` only when
  `step.getComplexStep() > -1` (minimize host↔device transfer).
- Strategy: apply gates as the same tensor-structured stride operation, but on
  device. For a k-qubit gate, each GPU thread handles one group of `2^k` coupled
  amplitudes (indices sharing the non-operand bits), reading the small
  `getMatrix()` from constant memory.

### 9.2 Backends

- **OpenCL** (portable, fits GraalVM/JavaFX/GluonHQ audience): JOCL or Panama
  bindings; kernels for 1- and 2-qubit gate application, plus general k-qubit.
- **Panama FFI → cuBLAS** (CUDA): represent each step as a batched gemv against
  the strided state; best performance on NVIDIA.
- **Panama FFI → Apple Metal** (macOS, important for GluonHQ): Metal compute
  shaders via Panama downcalls.
- Abstract behind an internal `GpuBackend` SPI so the same
  `GpuQuantumExecutionEnvironment` picks OpenCL / CUDA / Metal at runtime; fall
  back to the parallel CPU backend (§5) when no device is available.

### 9.3 Notes

- Single precision matches `Complex`'s `float`; offer double for accuracy.
- Gates with `hasOptimization()` (pure permutations/phases) map to index/scale
  kernels — cheaper than full gemv; preserve that fast path on-device.

---

## 10. Backend 7 — Approximate simulation: amplitude pruning

`ideas.md` §13. Drop amplitudes below a configurable threshold to bound state
size; approximate but enables larger circuits. Composes with the sparse and MPS
backends.

### 10.1 Integration

- Add a constructor parameter `double pruneThreshold` to
  `SparseQuantumExecutionEnvironment` (§4) and an MPS `truncTol` (§7) — pruning is
  most natural in the sparse map (drop entries with `abssqr < threshold²`) and as
  SVD truncation in MPS.
- Optionally a `PruningQuantumExecutionEnvironment` decorator wrapping the dense
  engine: after each `applyStep`, zero out `Complex[]` entries below threshold and
  renormalize. Implement as a subclass of `SimpleQuantumExecutionEnvironment`
  overriding only the post-step hook (requires extracting a small protected
  `afterStep(Complex[])` seam in `SimpleQuantumExecutionEnvironment` — a minimal,
  backward-compatible refactor; default no-op preserves current behavior).
- Report total pruned weight (fidelity proxy) on the `Result` via a side channel.

---

## 11. Testing Strategy

Primary oracle: `SimpleQuantumExecutionEnvironment` on small circuits.

- **Cross-validation harness** (new test utility, e.g. `test` source set
  `BackendEquivalenceTest`): for a set of circuits (Bell, GHZ, QFT 3–6 qubits,
  Grover small, teleportation, random Clifford circuits, random shallow circuits),
  run both `SimpleQuantumExecutionEnvironment` and the backend under test, then
  compare:
  - final probability vector from `Result` (per-qubit `getProbability` and, where
    available, full amplitude vector) within tolerance `1e-4` (float precision of
    `Complex`; tighter `1e-9` for the double-kernel parallel backend).
  - intermediate snapshots keyed by `getComplexStep()` where both record them.
- **Per-backend specifics**:
  - Sparse: assert identical to dense on circuits with exact (non-pruned) settings;
    separately assert pruning keeps fidelity ≥ 1−ε for chosen ε.
  - Parallel: assert **bit-exact** (or within `1e-9`) vs dense with the double
    kernel; run with parallelism 1, 2, N to catch races; stress with large strides.
  - Stabilizer: random Clifford circuits cross-checked against dense (densify
    stabilizer state for ≤ 12 qubits); verify non-Clifford gates throw/fall back.
  - MPS: exact match vs dense when `maxBond` ≥ 2^(n/2); for truncated runs assert
    reported truncation error bounds the actual L2 deviation.
  - DD: densify small DDs, compare to dense.
  - GPU: same cross-check; gate behind availability so CI without a device skips.
- **Property tests** (ties to `ideas.md` §10): random circuits preserve
  normalization (Σ|amp|² ≈ 1) on every backend; unitary-only circuits keep total
  probability.
- **Reuse existing decomposition**: tests must include circuits that exercise
  `decomposeStep`'s permutation insertion (non-adjacent CNOT/Toffoli) so backends
  that consume decomposed steps are validated against the same normal form the
  dense engine uses.

---

## 12. Performance Benchmarking

`ideas.md` §10 ("JMH microbenchmarks") and §13 (scalability charts).

- **JMH microbenchmarks** for: the inner matrix–vector multiply
  (`getNextProbability2` original vs W3.2 in-place vs parallel vs GPU), QFT, and a
  full Grover/Shor run. Vary `nQubits` (e.g. 4..28 for dense, up to 50 for MPS on
  shallow circuits).
- **Memory**: hook into the existing `Computations.printMemory()` for coarse heap
  tracking; add allocation-rate measurement (JMH `-prof gc`) to demonstrate the
  W3.2 allocation reduction.
- **Scalability chart generator**: time vs `nQubits` per backend on a fixed circuit
  family (random shallow, QFT, Clifford), emitting CSV/JSON for plotting. Sparse
  and stabilizer should show dramatically flatter curves on their favorable
  classes; MPS flat-until-entanglement.
- **Baseline**: every benchmark records the dense `SimpleQuantumExecutionEnvironment`
  number as the reference.

---

## 13. Risks

- **Complex is `float` and mutable.** Single precision limits achievable accuracy
  and tolerances; the mutable public fields mean in-place kernels (W3.2/parallel)
  must be careful not to mutate shared constants (`Complex.ZERO/ONE/...`). Always
  allocate fresh accumulators; never call `addr`/`addmulr` on a shared constant.
- **Parallelism correctness.** The dense loops write disjoint indices, but the
  recursive `vsub` construction and stride gather/scatter must be partitioned so
  no two tasks touch the same `answer` index. Verify with deterministic
  comparison at parallelism > 1.
- **Decomposition coupling.** `decomposeStep` mutates `Step.complexStep` and
  caches into `Program.setDecomposedSteps`. Backends sharing a `Program` instance
  must not corrupt the cache; treat decomposed steps as read-only and avoid
  re-decomposing concurrently.
- **Stabilizer scope creep.** Non-Clifford detection must be exhaustive (angle
  checks on `Rotation*`/`Cr`, plus `Toffoli`/`Oracle`/arithmetic gates) or results
  are silently wrong. Prefer fail-fast over fallback unless explicitly enabled.
- **MPS needs complex SVD.** No SVD exists in the codebase; either add a small
  dependency or implement and test a complex SVD carefully (numerical stability).
- **GPU/Panama maturity.** Panama FFI API surface and platform availability vary
  by JDK; OpenCL is the safe portable default, CUDA/Metal are opt-in. Must degrade
  gracefully to CPU.
- **Snapshot semantics.** `setIntermediateProbability` expects a full
  `Complex[dim]`. Large-n backends (MPS/stabilizer/sparse) cannot always
  materialize it; define a documented policy (per-qubit marginals or sampled
  histogram) so `Result` consumers aren't surprised.

---

## 14. Suggested Sequencing

1. **W3 Complex hot-path optimization** (`StateVector` interleaved buffers +
   in-place fused-multiply-add kernel using existing `Complex.addmulr`). Improves
   the dense engine and unblocks parallel/GPU. Lowest risk, immediate value.
2. **Parallel CPU backend** (§5). Reuses everything, no new representation, big
   speedup, easy to cross-validate bit-for-bit.
3. **Sparse backend** (§4) + **amplitude pruning** (§10) together. High value for
   Grover/Shor arithmetic; pruning rides on the sparse map.
4. **Stabilizer backend** (§6). Self-contained, no SVD/GPU dependencies, unlocks
   QEC research (Section 3 of `ideas.md`).
5. **MPS backend** (§7). Requires complex SVD; highest scalability payoff.
6. **GPU backend** (§9). Build on W3 buffers; OpenCL first, then CUDA/Metal via
   Panama.
7. **Decision-diagram backend** (§8). Most complex; last.

Each step lands behind a new class implementing `QuantumExecutionEnvironment`,
validated by the §11 cross-validation harness before the next begins.
