# Section 3 — Quantum Algorithms: Implementation Plan

Package: `org.redfx.strange.algorithm`. All circuits are built from the real Strange
API: `Program(nQubits, QuantumStep...)`, `program.addStep(Step)`, `new QuantumStep(name, Gate...)`,
`step.addGate(Gate)`, executed by `QuantumExecutionEnvironment.runProgram(Program)`
returning a `Result`. Source of truth for existing patterns: `algorithm/Classic.java`.

---

## 1. Overview & Goals

Add a broad family of teaching and research algorithms to the library, and modernise
the algorithm API along the way:

- **New algorithms** (in priority groups, see §8):
  oracle algorithms (Deutsch-Jozsa, Bernstein-Vazirani, Simon's), QPE, HHL,
  Quantum Counting, Quantum Walk, QAOA, VQE, QSVM / quantum kernels, qPCA,
  protocols (teleportation, superdense coding, BB84/E91 QKD), and error-correction
  codes (3-qubit bit-flip, Shor 9-qubit, Steane [[7,1,3]]).
- **API refactor**:
  1. Introduce a structured `AlgorithmResult` to replace `System.err.println` /
     `System.out.println` side-channels.
  2. Inject the `QuantumExecutionEnvironment` rather than relying on the static
     `Classic.qee` field — **without breaking existing static callers**.
  3. Expose iteration-by-iteration intermediate state (probabilities / qubit snapshots)
     for visualisation (Grover, QPE, quantum counting, quantum walk).

Constraint for the whole section: every circuit must be expressible with the existing
dense `SimpleQuantumExecutionEnvironment`. Where an algorithm needs gates or features
owned by another section (parametric gates, S/T, Estimator/Sampler, mid-circuit
measurement), that dependency is flagged in §6 and the algorithm is scheduled after it.

---

## 2. Current Pattern Analysis (cited from `algorithm/Classic.java`)

Everything new must mirror these patterns:

- **Static env field**: `private static QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();`
  with `setQuantumExecutionEnvironment(QuantumExecutionEnvironment val)` (lines 51–60).
  All methods call the static `qee.runProgram(program)`.
- **Trivial single-step program**: `randomBit()` (lines 67–73) —
  `new Program(1, new QuantumStep(new Hadamard(0)))`, run, then `result.getQubits()[0].measure()`.
- **Prep by X gates + QFT arithmetic**: `qsum(int a, int b)` (lines 82–126) builds a
  `prep` QuantumStep of `new X(idx)` to load classical integers into qubits, then
  `new QuantumStep(new Fourier(m, 0))`, a ladder of `new Cr(i, cr0, 2, 1+j)` controlled
  rotations, then `new QuantumStep(new InvFourier(m, 0))`. Read-out loops over
  `qubits[i].measure()` and reconstructs the integer with `1<<i`.
- **Oracle + diffusion (Grover)**: `searchProbabilities` (lines 168–206) builds an
  initial Hadamard layer (`s0.addGate(new Hadamard(i))` for all `i`), an `Oracle`
  matrix from a classical `Function<T,Integer>` via `createGroverOracle` (lines 295–307),
  a diffusion `Oracle` from `createDiffMatrix` (lines 309–333), and repeats
  `Oracle` / diffusion / `ProbabilitiesGate(0)` QuantumSteps `~ π√N/4` times. Reads
  `res.getProbability()` and squares amplitudes with `Complex.abssqr()`.
- **Controlled modular arithmetic + IQFT (Shor)**: `measurePeriod` (lines 265–293)
  uses `MulModulus`, wraps it in `new ControlledBlockGate(mul, offset, i)`, applies
  `new QuantumStep(new InvFourier(offset, 0))`, then reconstructs the measured register.
- **`System.err.println` smell**: `search` line 151, `findPeriod` line 222,
  `qfactor` lines 245, 251, 257 — exactly the output that `AlgorithmResult` replaces.

Building blocks confirmed from source:

| Gate | Constructor | Notes |
|------|-------------|-------|
| `Hadamard` | `new Hadamard(idx)` | single-qubit, caption "H" |
| `X` / `Z` / `Y` | `new X(idx)` etc. | single-qubit Paulis |
| `Cnot` | `new Cnot(control, target)` | `(a,b)` = control a, target b |
| `Cz` | `new Cz(a, b)` | controlled-Z |
| `Cr` | `new Cr(target, control, double exp)` or `new Cr(target, control, base, pow)` → phase `2π/base^pow` | controlled phase; used in QFT |
| `RotationX/Y/Z` | `new RotationX(double theta, idx)` | the only parametric gates today |
| `R` | `new R(double exp, idx)` or `new R(base, pow, idx)` | single-qubit phase |
| `Toffoli` | `new Toffoli(a, b, c)` | controls a,b → target c |
| `Swap` | `new Swap(a, b)` | |
| `Fourier` / `InvFourier` | `new Fourier(dim, idx)` / `new InvFourier(size, idx)` | QFT block over `dim` qubits from `idx` |
| `Oracle` | `new Oracle(Complex[][] matrix)` | arbitrary unitary on `log2(matrix.length)` qubits, `setCaption`, `setInverse` |
| `ControlledBlockGate` | `new ControlledBlockGate(BlockGate bg, idx, control)` | controlled application of a block (e.g. for QPE unitary powers) |
| `ProbabilitiesGate` | `new ProbabilitiesGate(idx)` | snapshots probabilities into the Result (PROBABILITY QuantumStep) |
| `Measurement` | `new Measurement(idx)` | mid-circuit; `Program.ensureMeasuresafe` forbids re-superposing a measured qubit |

`Result` API used: `getQubits()` → `Qubit[]` (each `.measure()` returns 0/1 from
`getProbability()`), `getProbability()` → `Complex[]` full state vector,
`getIntermediateProbability(step)`, `getIntermediateQubits()` (Map<step,Qubit[]>),
`measureSystem()` + `getMeasuredProbability()` for a correlated full-register sample.
`Complex.abssqr()` for probabilities; `Complex.tensor`, `Complex.mmul`,
`Complex.conjugateTranspose` for matrix construction (used in `createDiffMatrix`).

---

## 3. API Refactor

### 3.1 `AlgorithmResult` (new file `algorithm/AlgorithmResult.java`)

A structured, immutable-ish carrier returned by the new algorithm methods. Replaces
all `System.err.println` / `System.out.println` diagnostic output.

```java
package org.redfx.strange.algorithm;

import Complex;
import org.redfx.strange.Qubit;
import java.util.*;

public final class AlgorithmResult {
    private final boolean success;                 // algorithm-defined success flag
    private final Map<String, Object> values;      // primary answers (e.g. "secret", "phase", "period")
    private final List<Iteration> iterations;      // per-iteration snapshots (visualisation)
    private final List<String> log;                // human-readable diagnostics (was System.err)
    private final long elapsedNanos;               // timing
    private final Complex[] finalState;            // final probability vector if requested (may be null)

    // builder-style mutators are package-private; algorithms populate via a Builder.

    public static final class Iteration {          // one visualisable QuantumStep
        public final int index;
        public final String label;                 // e.g. "Grover iteration 3"
        public final double[] probabilities;       // |amplitude|^2 per basis state
        public final Qubit[] qubits;               // optional per-qubit snapshot
        public Iteration(int index, String label, double[] probabilities, Qubit[] qubits) { ... }
    }

    public static final class Builder {
        public Builder success(boolean b);
        public Builder put(String key, Object v);
        public Builder log(String msg);            // append diagnostic line (no stderr)
        public Builder iteration(Iteration it);
        public Builder finalState(Complex[] s);
        public AlgorithmResult build(long startNanos); // sets elapsedNanos = now - startNanos
    }

    // getters
    public boolean isSuccess();
    public <T> T get(String key, Class<T> type);
    public Optional<Object> getValue(String key);
    public List<Iteration> getIterations();
    public List<String> getLog();
    public long getElapsedNanos();
    public Complex[] getFinalState();
}
```

Design notes:
- Keep it dependency-free (only `org.redfx.strange.*`). No SLF4J here — §10 of `ideas.md`
  owns logging; `AlgorithmResult.log` is the in-band replacement that callers may print
  themselves.
- `Iteration.probabilities` is produced by squaring `result.getProbability()` /
  `result.getIntermediateProbability(step)` entries with `Complex.abssqr()` (same code
  path as `searchProbabilities`).
- `get(key, type)` does a checked cast so callers get typed answers
  (`r.get("secret", int[].class)`).

### 3.2 Dependency-injection of `QuantumExecutionEnvironment`

Goal: algorithms accept any `QuantumExecutionEnvironment` instead of reading the static
field, **without breaking existing static callers of `Classic`**.

Approach — *instance class + static facade*:

1. **New instance class `algorithm/Algorithms.java`** holds the env in a field:
   ```java
   public class Algorithms {
       private final QuantumExecutionEnvironment qee;
       public Algorithms() { this(new SimpleQuantumExecutionEnvironment()); }
       public Algorithms(QuantumExecutionEnvironment qee) { this.qee = Objects.requireNonNull(qee); }
       // all NEW algorithms are instance methods here, calling this.qee.runProgram(...)
       public AlgorithmResult deutschJozsa(int n, Function<int[],Integer> oracle) { ... }
       ...
   }
   ```
   This is the primary, testable, DI-friendly entry point. Every new algorithm lives
   here as an instance method (or delegates to a helper class — see §5 for large ones).

2. **`Classic` is left intact** (no behaviour change). Its existing static methods keep
   using the static `qee` field and `setQuantumExecutionEnvironment`. To avoid two
   sources of truth, refactor each existing static method body to delegate:
   ```java
   public static int randomBit() { return new Algorithms(qee).randomBitInternal(); }
   ```
   This is internal-only and preserves the public static signatures and the
   `setQuantumExecutionEnvironment` contract. **No existing caller breaks.**

3. **Static convenience overloads** on `Classic` (or `Algorithms` static methods) that
   take an explicit env, e.g.
   `Classic.deutschJozsa(QuantumExecutionEnvironment qee, int n, Function<...> f)`,
   so users who like the static style can still inject. These just construct an
   `Algorithms` and call the instance method.

Migration is therefore additive: `Algorithms` is the new home, `Classic` becomes a thin
backward-compatible facade. The static `qee` field stays only as the default for the
legacy static methods and is documented as deprecated-in-favour-of-`Algorithms`.

### 3.3 Iteration exposure for visualisation

Two mechanisms, both already supported by the engine:
- Insert `new QuantumStep(new ProbabilitiesGate(0))` (PROBABILITY QuantumStep) between algorithm
  iterations — exactly as Grover does (`Classic` lines 190–191). After running, read
  `result.getIntermediateProbability(stepIndex)` / `result.getIntermediateQubits()` and
  package each into an `AlgorithmResult.Iteration`.
- For algorithms run as a classical loop of separate `Program`s (QAOA/VQE outer loop,
  quantum counting sweeps), append one `Iteration` per outer loop pass.

---

## 4. Helper: oracle construction

Most oracle algorithms (DJ, BV, Simon, Grover-style) need a phase- or bit-oracle built
from a classical function. Add a small package-private helper class
`algorithm/Oracles.java` mirroring `Classic.createGroverOracle` (lines 295–307):

- `static Oracle phaseOracle(int n, Function<Integer,Integer> f)` — diagonal matrix,
  entry `(-1)^f(x)` (phase kickback form, like the Grover oracle but on `n` qubits).
- `static Oracle bitOracle(int nIn, int nOut, BiFunction<...> f)` — permutation/XOR
  oracle `|x>|y> -> |x>|y ⊕ f(x)>` as a `2^(nIn+nOut)` `Complex[][]` (each column has a
  single `Complex.ONE`). Used by DJ/BV/Simon with an ancilla register.

This keeps oracle algorithms short and consistent with the existing Grover code path.

---

## 5. Detailed per-algorithm work items (ordered)

Each item: file location, qubit layout, **step sequence in real API**, classical
post-processing, and what goes into `AlgorithmResult`.

### A. Deutsch-Jozsa — `Algorithms.deutschJozsa(int n, Function<Integer,Integer> f)`
- Qubits: `n` input + 1 ancilla → `Program(n+1)`.
- QuantumSteps:
  1. `Step prep`: `new X(n)` on ancilla, then `new Hadamard(i)` for `i in 0..n` (all incl. ancilla).
  2. `new QuantumStep(bitOracle(n,1,...))` (or `phaseOracle` on the input register).
  3. `Step post`: `new Hadamard(i)` for `i in 0..n-1`.
- Run, `result.getQubits()`, measure inputs `q[0..n-1]`.
- Post: if all measured 0 → **constant**, else **balanced**.
- `AlgorithmResult.put("type", constant|balanced)`, `put("bits", measured)`.

### B. Bernstein-Vazirani — `Algorithms.bernsteinVazirani(int n, Function<Integer,Integer> f)` where `f(x)=a·x ⊕ b`
- Same layout/structure as DJ (n input + 1 ancilla, H, oracle, H on inputs).
- Post: measured input register **is** the hidden string `a`.
- `put("secret", int[] a)`. Test oracle: `a·x mod 2`.

### C. Simon's algorithm — `Algorithms.simon(int n, Function<Integer,Integer> f)`
- Qubits: `2n` (n input + n output) → `Program(2n)`.
- QuantumSteps: H on `0..n-1`; `bitOracle(n,n,f)` mapping input→output register; H on `0..n-1`;
  insert `new Measurement(i)` for output qubits `n..2n-1` (mid-circuit measure allowed,
  output not re-superposed).
- Run repeatedly (`O(n)` shots via fresh `Program` runs / `result.measureSystem()`):
  each run yields a `y` with `y·s = 0 mod 2`. Collect `n-1` independent `y`s.
- Post (classical): Gaussian elimination over GF(2) (new helper in
  `local/Computations.java` or a private method) to solve for hidden period `s`.
- `put("period", int[] s)`, `iteration` per shot.

### D. Quantum Phase Estimation — `algorithm/PhaseEstimation.java`
- Inputs: counting register size `t`, a unitary supplied as `Oracle`/`BlockGate` `U`
  on `m` qubits, eigenstate prep on the target register.
- Qubits: `Program(t + m)`; counting `0..t-1`, eigenstate `t..t+m-1`.
- QuantumSteps:
  1. H on all counting qubits; prepare eigenstate on target (caller-supplied prep QuantumStep).
  2. For `j in 0..t-1`: apply `U^(2^j)` controlled on counting qubit `j` via
     `new ControlledBlockGate(uPower_j, t /*target start*/, j /*control*/)`
     (same pattern as Shor's `ControlledBlockGate(mul, offset, i)` in
     `Classic.measurePeriod`). `U^(2^j)` is precomputed by repeated `Complex.mmul` of
     `U.getMatrix()`, wrapped in an `Oracle`/`BlockGate`.
  3. `new QuantumStep(new InvFourier(t, 0))` on the counting register (exactly Shor's read-out).
- Post: measure counting register → integer `k`; phase estimate `φ = k / 2^t`.
- `put("phase", double)`, `put("k", int)`. Expose per-controlled-U `ProbabilitiesGate`
  snapshots as iterations.
- **Dependency**: clean QPE for arbitrary `U` benefits from the §4 Subcircuit/controlled
  abstraction and from `ControlledBlockGate` supporting arbitrary blocks; verify
  `ControlledBlockGate` accepts an `Oracle`-backed block, otherwise wrap `U` in a
  `BlockGate` subclass (small adapter).

### E. Quantum Counting — `algorithm/QuantumCounting.java`
- Combines QPE (D) with the Grover iteration operator `G = diffusion · oracle` (reuse
  `createGroverOracle` + `createDiffMatrix` logic from `Classic`, extracted to `Oracles`).
- Layout: `t` counting qubits + `n` search qubits; QPE on `G` as the unitary.
- Post: eigenvalue phase `θ` of `G` gives number of solutions
  `M = N · sin^2(θπ)` (N = 2^n). `put("count", int M)`, `put("theta", double)`.
- **Dependency**: QPE (D).

### F. Quantum Walk — `algorithm/QuantumWalk.java`
- Discrete-time coined walk on a line/cycle of `N=2^n` positions + 1 coin qubit.
- QuantumSteps per walk QuantumStep: coin flip `new Hadamard(coin)`; conditional shift implemented as
  a permutation/`Oracle` matrix (or controlled increment/decrement built from
  `AddInteger`/`ControlledBlockGate`) that maps `|c,x> -> |c, x±1>`.
- Loop `T` QuantumSteps, inserting `ProbabilitiesGate` snapshots → iterations for visualisation.
- `put("distribution", double[])` from `result.getProbability()`.

### G. HHL (linear systems) — `algorithm/HHL.java`
- Solves `A x = b` for small (2x2 / 4x4) Hermitian `A`.
- Registers: 1 ancilla (rotation) + `t` clock/phase qubits + `n_b` state qubits holding `b`.
- QuantumSteps:
  1. Prepare `|b>` on the state register (amplitude-load via an `Oracle` unitary, or
     `RotationY` for 1-qubit `b`).
  2. **QPE** (D) with `U = e^{iA t0}` (precompute matrix exponential of `A` classically
     into a `Complex[][]`, wrap as `Oracle`) to write eigenvalues into the clock register.
  3. Controlled rotation: for each clock value `λ`, apply `RY(2·arcsin(C/λ))` on the
     ancilla controlled on the clock register (built as a controlled-`Oracle` /
     decomposed `Cr`+`RotationY` ladder).
  4. **Inverse QPE** to uncompute the clock register (`setInverse(true)` on the QPE
     block, like `Fourier.inverse()`).
  5. `new Measurement(ancilla)`; post-select ancilla = 1.
- Post: state register amplitudes (read `result.getProbability()` conditioned on ancilla)
  ∝ `x`. `put("solution", double[])` (up to normalisation / global phase).
- **Dependencies**: QPE (D); controlled-RY rotation conditioned on a register
  (cleanly needs the §4 controlled-subcircuit abstraction; otherwise hand-build the
  eigenvalue-inversion `Oracle`). Heaviest algorithm — schedule last among the
  oracle/QPE family.

### H. QAOA — `algorithm/QAOA.java`
- For a cost Hamiltonian `H_C` (e.g. MaxCut on a graph) and `p` layers.
- Per layer `k`: cost unitary `e^{-iγ_k H_C}` (product of `Cz`/`new Cr`/`RotationZ`-style
  two-qubit phase terms per edge) then mixer `e^{-iβ_k H_B}` (a `new RotationX(2β_k, i)`
  on every qubit). Initial state: H on all qubits.
- Outer classical loop: pick `(γ,β)`, build the `Program`, run, compute
  `⟨H_C⟩` from `result.getProbability()` (or an Estimator), update params.
- `put("params", double[])`, `put("expectation", double)`, one `Iteration` per
  optimiser QuantumStep.
- **Dependencies (flag)**: needs §1 **parametric gates** (a unified `ParametricGate` so
  `γ,β` can be set/reset cheaply) and §7 **Estimator/Sampler + classical optimizer**
  (COBYLA/SPSA). Today only `RotationX/Y/Z` are parametric; the cost-layer two-qubit
  rotations (`ZZ(θ)`) do **not** exist yet → depends on §1 `ZZ` interaction gate, or be
  decomposed as `Cnot · RotationZ · Cnot`. **Schedule after §1 and §7.**

### I. VQE — `algorithm/VQE.java`
- Ansatz circuit (e.g. hardware-efficient: layers of `RotationY`/`RotationZ` + `Cnot`
  entanglers) parameterised by `θ`. Hamiltonian `H` given as a weighted sum of Pauli
  strings.
- Outer loop: set `θ`, run, estimate `⟨ψ(θ)|H|ψ(θ)⟩` per Pauli term (measure in the
  appropriate basis: append H for X-terms, `RotationX(-π/2)` for Y-terms before
  measuring), sum weighted expectations, minimise.
- `put("groundEnergy", double)`, `put("params", double[])`, iterations per QuantumStep.
- **Dependencies (flag)**: §1 parametric gates, §7 **Estimator** (`⟨ψ|H|ψ⟩` without
  collapse) and a classical optimizer. **Schedule after §1 and §7.**

### J. QSVM / quantum kernels — `algorithm/QuantumKernel.java`
- Feature map `Φ(x)`: a fixed parametric circuit encoding a data vector `x` (e.g.
  `RotationZ`/`RotationY` + entanglers). Kernel entry
  `K(x_i,x_j) = |⟨Φ(x_j)|Φ(x_i)⟩|^2` computed via the **compute-uncompute** circuit:
  build `Φ(x_i)` then `Φ(x_j)†` (`setInverse(true)`), run, read probability of the
  all-zero basis state from `result.getProbability()[0]`.
- Output: Gram matrix `double[][]` for an SVM (classical SVM solved outside, or
  delegate to Apache Commons Math). `put("kernelMatrix", double[][])`.
- **Dependency**: §1 parametric gates (feature map). Classical SVM is out of scope —
  return the kernel matrix.

### K. qPCA — `algorithm/QPCA.java`
- Given a density matrix `ρ` (as `Complex[][]`), use QPE on the unitary `e^{iρt}`
  (density-matrix exponentiation) to extract eigenvalues/eigenvectors of `ρ`.
- Reuses QPE (D); prepare multiple copies of `ρ` is approximated here by directly
  exponentiating `ρ` into an `Oracle` (simulator-only shortcut). `put("eigenvalues", double[])`.
- **Dependency**: QPE (D). Lower priority / research-grade.

### L. Quantum teleportation — `algorithm/Protocols.teleport(double alpha)` (or `Qubit` state)
- Qubits: 3 → `Program(3)`. q0 = state to teleport (init via `program.initializeQubit(0, alpha)`
  or a prep gate), q1/q2 = Bell pair.
- QuantumSteps: `new Hadamard(1)`, `new Cnot(1,2)` (Bell pair); `new Cnot(0,1)`,
  `new Hadamard(0)`; `new Measurement(0)`, `new Measurement(1)`; corrections on q2
  conditioned on the two measured bits — `new X(2)` if q1==1, `new Z(2)` if q0==1.
- **Dependency (flag)**: classical-controlled corrections need §7 **dynamic circuits**
  (`ClassicalRegister` + `If`). **Workaround for now**: read measured bits from
  `result.getQubits()` and apply corrections by running a short follow-up `Program`, or
  build the deferred-measurement version (apply `Cnot(1,2)`/`Cz(0,2)` corrections
  unitarily before measuring) which needs no classical control. Implement the
  deferred-measurement variant first.
- Verify q2 probability matches input `alpha`. `put("fidelity", double)`.

### M. Superdense coding — `algorithm/Protocols.superdense(int twoBits)`
- Qubits: 2. Bell pair (`new Hadamard(0)`, `new Cnot(0,1)`); Alice encodes 2 bits on q0
  with `{I, X, Z, XZ}` (`new X(0)` / `new Z(0)` as needed); Bob decodes with
  `new Cnot(0,1)`, `new Hadamard(0)`, then measures both. `put("decoded", int)`.
- No external dependency.

### N. BB84 / E91 QKD — `algorithm/QKD.java`
- BB84: classical loop over key bits. Per bit: random Alice bit + basis → 1-qubit
  `Program(1)` (`new X(0)` for bit 1, `new Hadamard(0)` for diagonal basis); Bob random
  basis (`new Hadamard(0)` if diagonal) then `new Measurement(0)`; sift where bases match.
  Optional eavesdropper = an intercept-resend `Measurement` inserted mid-channel to show
  error-rate increase.
- E91: distribute Bell pairs (`new Hadamard`, `new Cnot`), measure in randomly chosen
  bases, run CHSH check for eavesdropping.
- `put("siftedKey", int[])`, `put("qber", double)`, iterations per round.
- No gate dependency; relies on per-bit fresh `Program` runs.

### O. Error correction codes — `algorithm/ErrorCorrection.java`
- **3-qubit bit-flip**: `Program(3)` (+2 syndrome ancillas optional). Encode:
  `new Cnot(0,1)`, `new Cnot(0,2)`. Inject error (test): `new X(k)`. Syndrome:
  `new Cnot(0,3)`,`new Cnot(1,3)`,`new Cnot(0,4)`,`new Cnot(2,4)`; measure ancillas;
  correct with `new X` on the flagged qubit; decode. (Deferred-measurement / majority
  variant if dynamic control is unavailable.)
- **Shor 9-qubit**: tensor the bit-flip code with a phase-flip code; encode block of 3 ×
  3 qubits with `Hadamard`+`Cnot` patterns. Builds directly on the bit-flip method.
- **Steane [[7,1,3]]**: CSS code from the [7,4] Hamming code; encode via a fixed
  sequence of `Hadamard` and `Cnot` per the standard Steane encoder; syndrome via 6
  ancillas. Largest of the three.
- `put("corrected", boolean)`, `put("syndrome", int[])`.
- **Dependency (flag)**: full syndrome-decode-correct uses §7 dynamic circuits;
  implement the unitary/deferred-measurement versions first (no classical control), which
  the dense simulator supports.

---

## 6. Cross-section dependencies (summary)

| Algorithm | Depends on | Section | Mitigation if dep absent |
|-----------|-----------|---------|--------------------------|
| QAOA, VQE | `ParametricGate` interface; `ZZ`/`XX` interaction gates | §1 | Decompose `ZZ(θ)` as `Cnot·RotationZ·Cnot`; use existing `RotationX/Y/Z` |
| QAOA, VQE | Estimator/Sampler + classical optimizer | §7 | Compute `⟨H⟩` directly from `result.getProbability()`; bundle a simple SPSA/grid search |
| QSVM | Parametric feature-map gates | §1 | Use `RotationX/Y/Z` only |
| HHL, qPCA, Quantum Counting | QPE | §3 D (internal) | Build QPE first |
| Teleportation, BB84 eavesdrop, error correction | Dynamic circuits (`ClassicalRegister`, `If`) | §7 | Implement deferred-measurement / unitary-correction variants first |
| All | `AlgorithmResult`, DI `Algorithms` | §3 internal | Build these first (foundation) |
| Visualisation iterations | `ProbabilitiesGate` (exists) + `Result.getIntermediateProbability` (exists) | core | none |

QPE (D) is the internal lynchpin: HHL, qPCA, and Quantum Counting all consume it, so it
must land before them.

---

## 7. Testing strategy

Mirror existing test style; use deterministic known-answer checks (probabilities are
deterministic in `SimpleQuantumExecutionEnvironment`; only `measure()` sampling is
random, so assert on `getProbability()` where possible).

- **AlgorithmResult / DI**: unit-test that `Classic.randomBit()` still works (regression),
  that `new Algorithms(customEnv)` routes `runProgram` to the injected env (mock/spy env
  counts calls), and that builders populate `iterations`/`log` correctly.
- **Deutsch-Jozsa**: constant oracle (`f≡0` and `f≡1`) → all input qubits measure 0
  (probability mass on basis state 0). Balanced oracle → input register ≠ 0 with prob 1.
  Assert `AlgorithmResult.get("type")`.
- **Bernstein-Vazirani**: for several hidden strings `a` (e.g. `0b101`), assert recovered
  `secret == a` with probability 1 (check amplitude on basis state `a`).
- **Simon's**: oracle with known period `s`; assert every sampled `y` satisfies
  `y·s == 0 mod 2`, and that GF(2) solver recovers `s`.
- **QPE**: `U = Z`-phase gate / `R(phase)` with known eigenphase (e.g. `φ=1/4` with
  `t=3` → exact basis state `010`); assert measured `k/2^t == φ`.
- **Quantum Counting**: search space with known `M` solutions; assert recovered `M`
  within rounding.
- **HHL**: 2x2 system with known `x`; assert solution ratio (post-selected) matches
  classical solve within epsilon (tolerant compare like a future `QuantumAssert`).
- **Teleportation**: init q0 with `alpha`; assert q2 probability == `alpha^2` within eps.
- **Superdense**: all 4 two-bit messages round-trip to the correct decoded value.
- **Error correction**: inject single `X` (then single `Z` for Shor/Steane) on each
  qubit; assert decoded logical qubit matches original for every single-error location;
  assert an uncorrectable double error is *not* corrected (negative test).
- **BB84**: with no eavesdropper, sifted keys match and QBER ≈ 0; with intercept-resend
  eavesdropper, QBER ≈ 0.25.
- **QAOA/VQE**: small MaxCut (triangle) / H2-like 1-qubit Hamiltonian with known optimum;
  assert the optimiser reaches the known ground energy within tolerance (looser, may be
  flaky → mark as integration test).

Use tolerant floating comparison everywhere (`Math.abs(a-b) < 1e-6`), anticipating the
`QuantumAssert` utility from §10.

---

## 8. Suggested sequencing

1. **Foundation**: `AlgorithmResult`, `Algorithms` (DI), `Oracles` helper, refactor
   `Classic` to delegate (no behaviour change). Wire iteration snapshots via
   `ProbabilitiesGate`.
2. **Oracle family** (low risk, pure existing gates): Deutsch-Jozsa, Bernstein-Vazirani,
   Simon's. Plus protocols **Superdense coding** and **Teleportation** (deferred-measure).
3. **QPE** (`PhaseEstimation`) — unblocks the rest.
4. **QPE consumers**: Quantum Counting, Quantum Walk, then HHL, then qPCA.
5. **Error correction**: 3-qubit bit-flip → Shor 9-qubit → Steane.
6. **QKD**: BB84 then E91.
7. **Variational (gated on §1 + §7)**: QSVM/kernels, then QAOA, then VQE.

---

## 9. Risks

- **`ControlledBlockGate` with arbitrary unitaries**: QPE needs controlled-`U^(2^j)`;
  confirm `ControlledBlockGate(BlockGate, idx, control)` accepts an `Oracle`/`BlockGate`
  carrying a precomputed `U^(2^j)` matrix. If it assumes arithmetic blocks (like
  `MulModulus`), write a thin `BlockGate` adapter wrapping the matrix. **Verify early.**
- **Dense-simulator scaling**: HHL/QPE/Steane push qubit counts (Steane = 7 data + 6
  ancilla = 13 qubits → 2^13 vector, fine; HHL with a 4-qubit clock + state + ancilla is
  the practical ceiling). Keep test instances tiny; flag in Javadoc.
- **Dynamic-circuit gap**: teleportation, error correction, BB84-eavesdrop ideally need
  classical-conditioned gates (§7). Deferred-measurement variants are correct on the
  simulator but differ from textbook circuits — document the difference to avoid
  confusing learners.
- **Variational dependency creep**: QAOA/VQE cannot be *fully* idiomatic without §1
  parametric-gate unification and §7 Estimator/optimizer. Building them early forces
  ad-hoc rebuilds of `Program` per parameter set (slow, no gradients). **Do not start
  before §1/§7 land.**
- **`measureSystem` correlation**: Simon/error-correction read multiple correlated qubits
  — use `result.measureSystem()` + `getMeasuredProbability()` for a *single consistent*
  full-register sample rather than independent `qubit.measure()` calls (which sample each
  qubit's marginal independently and break correlations). **Important correctness point.**
- **Backward compatibility**: the `Classic`→`Algorithms` delegation must preserve exact
  public signatures and the `setQuantumExecutionEnvironment` semantics; cover with a
  regression test before refactoring.

---

## 10. Files to create

```
algorithm/AlgorithmResult.java        (§3.1)
algorithm/Algorithms.java             (§3.2 — DI host for all new algorithms)
algorithm/Oracles.java                (§4 — phase/bit oracle helpers)
algorithm/PhaseEstimation.java        (§5 D)
algorithm/QuantumCounting.java        (§5 E)
algorithm/QuantumWalk.java            (§5 F)
algorithm/HHL.java                    (§5 G)
algorithm/QAOA.java                   (§5 H — gated on §1/§7)
algorithm/VQE.java                    (§5 I — gated on §1/§7)
algorithm/QuantumKernel.java          (§5 J — gated on §1)
algorithm/QPCA.java                   (§5 K)
algorithm/Protocols.java              (§5 L,M — teleport, superdense)
algorithm/QKD.java                    (§5 N — BB84/E91)
algorithm/ErrorCorrection.java        (§5 O — bit-flip, Shor, Steane)
```
Modified (additive only): `algorithm/Classic.java` (delegate to `Algorithms`, keep
signatures), optionally `local/Computations.java` (GF(2) solve for Simon, matrix
exponential helper for HHL/qPCA).
