# Section 8 — Quantum Information & Tomography: Implementation Plan

Package: `org.redfx.strange`. Target sub-package for everything new in this section: `org.redfx.strange.info` (new directory `info/` at repo root, mirroring the flat layout). Tomography circuit/protocol helpers that drive `runProgram` go in `info/`; pure linear-algebra primitives extend the existing `Complex` / `local.Computations` classes.

---

## 1. Overview & Goals

Two distinct families of features, with very different inputs:

1. **State-derived quantities (white-box, exact).** Computed directly from the final simulated state vector `Complex[] probability` returned by `Result`. No sampling. These are: Bloch coordinates, von Neumann entropy, concurrence, negativity. They require a **partial trace** and (for entropy/negativity) **eigenvalues of a small Hermitian / real-symmetric matrix**.

2. **Measurement-derived protocols (black-box, statistical).** Built on top of `QuantumExecutionEnvironment.runProgram` by appending pre-measurement basis-rotation gates, running many shots, and reconstructing. These are: state tomography, process tomography, randomised benchmarking (RB), cross-entropy benchmarking (XEB). They treat the circuit as an unknown channel and only consume measurement counts.

Goal: deliver (1) fully (it is cheap, exact, and feeds Section 11 visualisation), and (2) as a layered protocol API where each protocol reuses a common `Sampler`/counts helper. Honour the library's **minimal-dependency philosophy**: implement the small numerical kernels (2×2/4×4 eigen-decomposition, partial trace, partial transpose) in-house rather than pulling EJML/Commons-Math. A dependency is only justified if we later need general N×N eigensolvers for *n*-qubit tomography of large registers (see Risks).

### Key facts about the existing code (cited)

- `Result.getProbability()` returns the final `Complex[]` amplitude vector (length `2^n`). It is set by `SimpleQuantumExecutionEnvironment.runProgram` via `result.setIntermediateProbability(...)`. This is a **pure state vector** `|ψ⟩` — the simulator is a dense state-vector simulator (no density matrices). (`Result.java:134`, `SimpleQuantumExecutionEnvironment.java:100,114`).
- Amplitude index convention: in `SimpleQuantumExecutionEnvironment.runProgram` (lines 75-88) and in `calculateQubitStatesFromVector` (`Result.java:164`), bit `i` of the basis index uses `div = 1 << i` with `answer[i]` accumulating when `(j/div)%2 == 1`. Index `i` therefore corresponds to qubit `i`, **little-endian within `calculateQubitStatesFromVector`**, but the initial-state builder (lines 78-86) uses `pw = nQubits - j - 1` (big-endian). **This convention mismatch must be pinned down by a test before any partial-trace code is written** (see Risks §9) — get it wrong and every reduced density matrix is transposed/qubit-swapped.
- `Result.measureSystem()` (`Result.java:188`) performs a single projective measurement in the computational (Z) basis by sampling `probability[i].abssqr()`, then writes per-qubit booleans via `qubits[i].setMeasuredValue(...)`. This is the only measurement primitive today; tomography needs **repeated** Z-basis sampling, which we get by re-running the program or by sampling the probability vector N times directly.
- Available linear algebra on `Complex` (`Complex.java`): `mul`, `add`, `min`, `mul(double)`, `abssqr`, `identityMatrix(dim)`, `tensor(a,b)` (Kronecker product, line 201), `mmul`/`slowmmul` (matrix multiply, line 230), `conjugateTranspose` (line 269). `Computations` adds `createIdentity`, `permutateVector`, `gcd`, `getInverseModulus`, `calculateQubitStatesFromVector`.
- **Missing** (must be added): conjugate of a single `Complex`; trace; partial trace; partial transpose; eigenvalues/eigenvectors of a Hermitian matrix; matrix `x·log(x)` (for entropy) and sqrt (for concurrence). None of these exist anywhere in the codebase.
- Single-qubit gates extend `SingleQubitGate` (abstract, `getMatrix()` at `gate/SingleQubitGate.java:123`); `Hadamard`, `X`, `Y`, `Z`, `R` (phase gate, `gate/R.java:55` `R(double exp,int idx)`), `RotationX/Y/Z` exist. A general single-qubit basis-change gate can be built from these or from a new `SingleQubitMatrixGate` (already present, `gate/SingleQubitMatrixGate.java`).
- Circuits are built as `Program(nQubits, Step...)`, steps as `Step(Gate...)`, gates added with `step.addGate(...)` / `program.addStep(...)` (`Program.java:73,117`; `Step.java:85,136`).

---

## 2. Current Capabilities & Gaps

| Need | Exists? | Where / Gap |
|------|---------|-------------|
| Final state vector `|ψ⟩` | ✅ | `Result.getProbability()` |
| Per-qubit Z-probabilities | ✅ | `Result.calculateQubitStatesFromVector` / `Computations` copy |
| Single projective Z measurement | ✅ | `Result.measureSystem()` |
| Matrix multiply, tensor, conj-transpose | ✅ | `Complex.mmul`, `Complex.tensor`, `Complex.conjugateTranspose` |
| `Complex.conjugate()` (single number) | ❌ | add helper |
| Trace of a matrix | ❌ | add `Computations.trace` |
| **Partial trace** (reduce to subsystem density matrix) | ❌ | core new primitive |
| Outer product `|ψ⟩⟨ψ|` → density matrix | ❌ | add `Computations.densityMatrix(Complex[])` |
| **Eigenvalues** of Hermitian matrix | ❌ | Jacobi eigensolver (new) |
| **Partial transpose** (for negativity) | ❌ | new primitive |
| Matrix sqrt (concurrence) | ❌ | via eigendecomposition or closed-form 2-qubit |
| Basis-rotation pre-measurement gates (X/Y bases) | partial | H gives X-basis; need S†·H for Y-basis |
| Repeated sampling / shot counts | ❌ | add `Sampler` (sample prob vector N×) |
| Clifford group enumeration / random Clifford | ❌ | needed for RB; depends on Section 1 S/T gates |
| Channel / process matrix (chi, Choi) | ❌ | process tomography |

---

## 3. Detailed Work Items

Ordered by dependency. Items A–C unblock everything; D–E (state-derived measures) are independent of F–H (measurement protocols).

### A. Linear-algebra primitives — `local/Computations.java` (+ `Complex.java`)

Add to `Complex` (instance method):
```java
public Complex conjugate() { return new Complex(this.r, -this.i); }
```

Add to `Computations` (static methods, pure, no simulator state):

```java
// Density matrix rho = |psi><psi|  (dim x dim, dim = psi.length)
public static Complex[][] densityMatrix(Complex[] psi)

// Trace of a square matrix
public static Complex trace(Complex[][] m)

// Partial trace: trace out the listed qubit indices from an n-qubit
// density matrix, returning the reduced matrix on the remaining qubits.
// keep = sorted list of qubit indices to KEEP. nQubits = log2(rho.length).
// Convention MUST match the simulator's amplitude-index bit ordering
// (pin via test — see Risks). Implementation: iterate over basis indices
// of kept and traced subsystems, sum rho[row][col] over equal traced bits.
public static Complex[][] partialTrace(Complex[][] rho, int nQubits, int[] keep)

// Partial transpose over subsystem B (qubits in `subB`) of a bipartite
// density matrix. Used by negativity. Swaps the B-indices of bra/ket.
public static Complex[][] partialTranspose(Complex[][] rho, int nQubits, int[] subB)

// Real eigenvalues of a Hermitian matrix via cyclic Jacobi rotations.
// Returns eigenvalues only (sorted ascending). Tolerance ~1e-10, max sweeps ~100.
// For Hermitian input we operate on the 2N x 2N real symmetric embedding
// [[Re, -Im],[Im, Re]] (eigenvalues come in duplicate pairs; take every other),
// OR implement complex Hermitian Jacobi directly. Real symmetric Jacobi is
// simplest and sufficient since all matrices here are <= 8x8 (3 qubits) for
// reduced states and 4x4 for 2-qubit measures.
public static double[] hermitianEigenvalues(Complex[][] herm)
```

**Why Jacobi:** matrices involved are tiny (reduced density matrices: 2×2 for one kept qubit, 4×4 for two; concurrence works on a fixed 4×4; negativity partial-transpose on 4×4). Jacobi is ~30 lines, dependency-free, numerically robust for small symmetric matrices, and converges quadratically. No need for QR/Householder or an external library.

**Partial-trace formula (keep set K, trace set T, n qubits):**
For reduced row `a` and column `b` (indexing the kept subsystem), `rho_red[a][b] = Σ_t rho[ idx(a,t) ][ idx(b,t) ]` where `t` ranges over all bit-patterns of the traced qubits and `idx(x,t)` interleaves the kept bits `x` and traced bits `t` back into the full n-qubit index according to qubit positions. Implement `idx` with explicit bit placement using `1 << qubit`.

Tests for A live in a new `info/test`-style harness (the repo has no test dir yet — create `info/` demos or a JUnit module if the build adds one; see Testing §6).

### B. Bloch coordinates — `info/BlochSphere.java`

Single-qubit Bloch vector from a (possibly mixed) single-qubit reduced density matrix `ρ`:
```
x = 2·Re(ρ01) = ρ01 + ρ10        (since ρ Hermitian, ρ10 = conj(ρ01))
y = 2·Im(ρ10) = i(ρ01 − ρ10)
z = ρ00 − ρ11
```
Purity check: `x²+y²+z² = 1` for pure, `< 1` for mixed (entangled-with-rest) qubits.

API:
```java
public final class BlochSphere {
    public static double[] coordinates(Result r, int qubit);        // {x,y,z}
    public static double[] coordinates(Complex[] stateVector, int nQubits, int qubit);
    public static double[][] allCoordinates(Result r);              // one row per qubit
}
```
Implementation: `densityMatrix(stateVector)` → `partialTrace(rho, n, new int[]{qubit})` → read 2×2 entries. For Section 11 (visualisation) this is the single consumer-facing entry point. Add a convenience `Result.blochCoordinates(int qubit)` thin delegate only if we are allowed to touch `Result.java` — **plan keeps it in `BlochSphere` to avoid core churn.**

### C. Entanglement measures — `info/Entanglement.java`

```java
public final class Entanglement {
    // von Neumann entropy S(rho_A) = -Σ λ_i log2 λ_i of the reduced state on `keep`.
    // Pure global state ⇒ this is the entanglement entropy across the bipartition.
    public static double vonNeumannEntropy(Complex[] state, int nQubits, int[] keep);

    // Concurrence for a 2-qubit pure or mixed state (4x4 rho).
    public static double concurrence(Complex[][] rho2q);
    public static double concurrence(Complex[] twoQubitState); // pure shortcut

    // Negativity N(rho) = (||rho^{T_B}||_1 - 1)/2 = Σ |negative eigenvalues of rho^{T_B}|.
    public static double negativity(Complex[][] rho, int nQubits, int[] subB);
}
```

**von Neumann entropy:** `rho_A = partialTrace(densityMatrix(state), n, keep)`; `λ = hermitianEigenvalues(rho_A)`; `S = -Σ λ_i·log2(λ_i)` skipping `λ_i ≤ 1e-12`. Units: bits (log base 2). Bell state across a 1|1 cut ⇒ eigenvalues {½,½} ⇒ S = 1.

**Concurrence (Wootters), 2-qubit:** spin-flipped state `ρ̃ = (Y⊗Y) ρ* (Y⊗Y)` where `ρ*` is the elementwise conjugate. Let `λ₁≥λ₂≥λ₃≥λ₄` be the square roots of the eigenvalues of `ρρ̃` (a non-Hermitian product — but its eigenvalues are real, non-negative; compute via eigenvalues of the Hermitian `√ρ · ρ̃ · √ρ`, or for the **pure-state shortcut** use `C = 2|α00·α11 − α01·α10|` from the two-qubit amplitudes, which avoids any eigensolver and is exact). Implement the pure shortcut first (covers most use cases and the Bell test), and the full mixed-state Wootters path second.

**Negativity:** `rhoTB = partialTranspose(densityMatrix(state), n, subB)`; eigenvalues via `hermitianEigenvalues` (partial transpose of Hermitian ρ is Hermitian, eigenvalues real); `N = Σ |λ_i| for λ_i < 0`. Bell state ⇒ one eigenvalue −½ ⇒ N = ½.

### D. Sampler / counts helper — `info/Sampler.java`

Foundation for all measurement protocols. Z-basis sampling from a state vector or via repeated `runProgram`:
```java
public final class Sampler {
    // Sample the computational-basis distribution N times from a finished Result's
    // probability vector. Returns counts indexed by basis integer (0..2^n-1).
    public static int[] sampleCounts(Result r, int shots);
    public static int[] sampleCounts(Complex[] probability, int shots);

    // Run a Program `shots` times on the given QEE, returning basis-index counts.
    // (Re-runs are independent; reuses runProgram + measureSystem logic.)
    public static int[] run(Program p, QuantumExecutionEnvironment qee, int shots);
}
```
`sampleCounts` reuses the cumulative-probability sampling already in `Result.measureSystem` (`Result.java:197-205`) — factor that loop into a reusable static sampler rather than duplicating. (If we may not edit `Result`, replicate the 6-line cumulative loop here.)

### E. Basis-rotation gates — `info/MeasurementBasis.java`

Pre-measurement rotations that map an X/Y eigenbasis onto the computational (Z) basis, so a standard Z-basis sample yields the X/Y outcome:
- **Z basis:** no rotation (identity).
- **X basis:** apply `H` before measuring (`H` maps |+⟩,|−⟩ → |0⟩,|1⟩).
- **Y basis:** apply `S†` then `H` (i.e. `H·S†`). `S†` is the inverse phase gate = `R(-π/2)` via existing `R(double exp,int idx)` with the appropriate exponent, OR `RotationZ(-π/2)` up to global phase. Verify which existing gate gives exactly `S† = diag(1,-i)`; if none, add a tiny `SingleQubitMatrixGate`-based `Sdg` here (Section 1 will add proper `S`/`T`/`S†` — depend on it when available, otherwise inline).

```java
public enum Pauli { X, Y, Z }
public final class MeasurementBasis {
    // Append the rotation that diagonalises `basis` on `qubit` to a fresh Step.
    public static void appendRotation(Program p, int qubit, Pauli basis);
}
```

### F. State tomography — `info/StateTomography.java`

Reconstruct ρ of an `n`-qubit register prepared by a (prefix) `Program`.

1. **Build measurement circuits.** For every assignment of a Pauli basis ∈ {X,Y,Z} to each of the `n` qubits (3ⁿ settings), clone the preparation program, append the corresponding `MeasurementBasis` rotations, and add a final measurement Step.
2. **Collect counts** via `Sampler.run` (S shots per setting). Convert counts → expectation values of each Pauli string `⟨P⟩` (P ∈ {I,X,Y,Z}ⁿ): for a setting, the ⟨P⟩ for any P obtained by replacing some measured axes with I is the parity expectation over the measured bits.
3. **Reconstruct.**
   - **Linear inversion:** `ρ = (1/2ⁿ) Σ_P ⟨P⟩ · P` (Pauli expansion). Cheap, exact in the infinite-shot limit, but can yield non-physical (negative-eigenvalue) ρ at finite shots.
   - **MLE projection (Smolin–Gambetta–Smith):** project the linear-inversion ρ onto the nearest physical (PSD, trace-1) density matrix by eigendecomposing (`hermitianEigenvalues` + eigenvectors — extend Jacobi to also return vectors here), clamping negatives, and renormalising. Implement linear inversion first; MLE as a follow-up flag.

```java
public final class StateTomography {
    public static Complex[][] reconstruct(Program prep, int nQubits,
            QuantumExecutionEnvironment qee, int shotsPerSetting); // linear inversion
    public static Complex[][] reconstructMLE(...);                  // + PSD projection
    public static double fidelity(Complex[][] rho, Complex[] pureTarget); // <psi|rho|psi>
}
```
Note: extending `hermitianEigenvalues` to return eigenvectors (a `record Eigen(double[] values, Complex[][] vectors)`) is required for MLE and for the mixed-state concurrence; do it once in item A.

### G. Process tomography — `info/ProcessTomography.java`

Characterise an unknown single- or two-qubit channel realised as a `Program` fragment (a `UnaryOperator<Program>` that appends the channel under test to a given input-prep program).

1. **Input states:** prepare a tomographically complete set per qubit — the 4 states {|0⟩,|1⟩,|+⟩,|+i⟩} (or the 6 Pauli eigenstates for robustness). For `k` qubits this is `4ᵏ` (or `6ᵏ`) preparations.
2. For each input, run **state tomography** (item F) on the channel output to get ρ_out.
3. **Reconstruct the χ (chi) / process matrix** by linear inversion: express each output ρ_out in the Pauli-operator basis and solve `ρ_out = Σ_mn χ_mn P_m ρ_in P_n†`. For one qubit this is a 16-parameter linear system (4×4 χ) solvable with the fixed, precomputed inverse of the (constant) `A`-matrix relating inputs to the Pauli basis — precompute `A⁻¹` analytically for 1 qubit; for 2 qubits build it programmatically. Alternatively reconstruct the **Choi matrix** `J = Σ_ij |i⟩⟨j| ⊗ E(|i⟩⟨j|)` directly, which only needs the channel applied to a basis of input density operators and is conceptually simpler; convert Choi↔chi as needed.

```java
public final class ProcessTomography {
    public static Complex[][] choiMatrix(UnaryOperator<Program> channel, int nQubits,
            QuantumExecutionEnvironment qee, int shotsPerSetting);
    public static Complex[][] chiMatrix(...);
    public static double processFidelity(Complex[][] chi, Gate idealGate);
}
```
Scope guard: ship **1-qubit** process tomography first; 2-qubit is the same machinery at 4× the dimension but exercises the index bookkeeping hard.

### H. Randomised benchmarking — `info/RandomizedBenchmarking.java`

Clifford-based average-gate-fidelity estimate, robust to SPAM.

1. **Clifford group.** Need the single-qubit Clifford group (24 elements) generated from {H, S} (and {H,S,CNOT} for multi-qubit). **Depends on Section 1 adding `S`/`S†`/`T`** (the phase gate exists as `R`, but a clean `S` is wanted). Enumerate the 24 1-qubit Cliffords once as `Gate`/`Program` fragments and store with their 2×2 matrices (for computing inverses).
2. **Sequence generation.** For each sequence length `m ∈ {1,2,4,...}`: draw `m` random Cliffords `C₁..C_m`, compute the **global inverse** `C_inv = (C_m···C₁)†` (matrix-multiply the stored matrices via `Complex.mmul`, then find the matching Clifford — exact match since the group is closed), append it so the ideal net operation is identity.
3. **Run** `Sampler.run` for K random sequences per length; record survival probability `P(return to |0…0⟩)`.
4. **Fit** `P(m) = A·p^m + B` (least squares on log, or a 3-param fit via a tiny Levenberg–Marquardt / grid search — keep dependency-free). Average error per Clifford `r = (1−p)(1 − 1/d)`, `d = 2ⁿ`.

```java
public final class RandomizedBenchmarking {
    public record RBResult(int[] lengths, double[] survival, double p, double averageError) {}
    public static RBResult run(int nQubits, int[] sequenceLengths, int seqPerLength,
            int shots, QuantumExecutionEnvironment qee);
}
```
On a noiseless `SimpleQuantumExecutionEnvironment` the decay is trivially `p≈1` (fidelity ≈ 1); RB only becomes meaningful once Section 5 noise models exist — but the protocol and fit must still be correct and testable (noiseless ⇒ `p=1`, `r=0`).

### I. Cross-entropy benchmarking — `info/CrossEntropyBenchmarking.java`

Google-style linear XEB fidelity.

1. Generate a random circuit `U` (random single-qubit gates + entanglers) on `n` qubits as a `Program`.
2. Compute the **ideal probabilities** `p_ideal(x) = |⟨x|U|0⟩|²` directly from the simulated `Result.getProbability()` (white-box advantage we have).
3. **Sample** measured bitstrings from the (possibly noisy) execution via `Sampler.run`.
4. **Linear XEB fidelity:** `F_XEB = (2ⁿ · ⟨p_ideal(x_measured)⟩_samples) − 1`, averaging `p_ideal` evaluated at the *measured* outcomes.

```java
public final class CrossEntropyBenchmarking {
    public static double linearXEB(Program randomCircuit, int nQubits,
            QuantumExecutionEnvironment qee, int shots);
}
```
Noiseless ⇒ `F_XEB ≈ 1`. Uniform-random outcomes ⇒ `F_XEB ≈ 0`. Both are direct unit tests.

---

## 4. File / Path Summary

| File | Item | Notes |
|------|------|-------|
| `Complex.java` (edit) | A | add `conjugate()` |
| `local/Computations.java` (edit) | A | `densityMatrix`, `trace`, `partialTrace`, `partialTranspose`, `hermitianEigenvalues` (+ eigenvectors variant) |
| `info/BlochSphere.java` | B | new |
| `info/Entanglement.java` | C | new |
| `info/Sampler.java` | D | new |
| `info/Pauli.java`, `info/MeasurementBasis.java` | E | new |
| `info/StateTomography.java` | F | new |
| `info/ProcessTomography.java` | G | new |
| `info/RandomizedBenchmarking.java` | H | new; needs Section 1 `S` |
| `info/CrossEntropyBenchmarking.java` | I | new |

Keep edits to `Complex` / `Computations` additive (new static methods only) to avoid disturbing the simulation hot path. Do **not** touch `Result.java` if avoidable — wrap, don't modify.

---

## 5. Cross-Section Dependencies

- **Section 1 (Gate library):** RB needs a clean `S`/`S†` (and ideally `T`) and a Clifford generator set; multi-qubit RB needs `CNOT` (exists). The Y-basis rotation in item E uses `S†`. Until Section 1 lands, inline a local `Sdg` via `SingleQubitMatrixGate` = `diag(1,-i)`.
- **Section 5 (Noise models):** RB, XEB, and process/state tomography are only *physically interesting* on a noisy backend. They must be correct on the noiseless simulator (ideal-fidelity baselines) and become useful when noise arrives. No hard code dependency — they operate through `QuantumExecutionEnvironment`, so any future noisy QEE drops in.
- **Section 11 (Visualisation):** consumes `BlochSphere.allCoordinates(Result)` (Bloch data export) and could consume the tomography ρ and RB decay curves. `BlochSphere` is the contract; keep its output a plain `double[]/double[][]`.
- **Section 7 (Estimator/Sampler API):** item D `Sampler` overlaps with the proposed §7 `Sampler`. Coordinate so there is **one** sampler type — if §7 ships first, reuse it; otherwise §8's `Sampler` is the seed.

---

## 6. Testing Strategy

No test directory exists yet (`find` for tests returns nothing). Add JUnit tests under a conventional `test/` (or a small `info/` `main`-method demo harness if the build is not yet Mavenised). Concrete oracles:

**Linear algebra (item A):**
- `partialTrace` of `|00⟩` keeping qubit 0 ⇒ `diag(1,0)`.
- `partialTrace` of the Bell state `(|00⟩+|11⟩)/√2` keeping either qubit ⇒ `½·I` (maximally mixed).
- `hermitianEigenvalues(½·I)` ⇒ {½,½}; of `diag(1,0)` ⇒ {0,1}.
- **Pin the index convention:** prepare a product state where qubit 0 is `|1⟩` and qubit 1 is `|0⟩`, assert `partialTrace` returns the right single-qubit ρ for each — this catches the big/little-endian mismatch noted in §2.

**Bloch (B):** `|0⟩`→(0,0,1); `|1⟩`→(0,0,−1); `|+⟩`(=H|0⟩)→(1,0,0); `|+i⟩`(=S·H|0⟩)→(0,1,0). One qubit of a Bell state → (0,0,0) (centre of sphere, fully mixed).

**Entanglement (C):**
- Bell state, 1|1 cut: `vonNeumannEntropy = 1.0` bit (±1e-9). Product state: `0.0`.
- Bell state `concurrence = 1.0`; product state `0.0`; pure shortcut and Wootters path must agree.
- Bell state `negativity = 0.5`; product `0.0`.
- GHZ `(|000⟩+|111⟩)/√2`: entropy of any single qubit = 1 bit.

**State tomography (F):** prepare a known random single-qubit state (and a Bell state), reconstruct with large `shots` (e.g. 1e5), assert `fidelity(rho, target) > 0.99`. With infinite-shot (use exact expectation values, bypassing sampling) the reconstruction must be exact to 1e-9 — add a deterministic mode for this test.

**Process tomography (G):** identity channel ⇒ χ has a single 1 in the `I,I` entry; an `X` gate ⇒ χ concentrated on `X,X`. Process fidelity vs ideal `> 0.99` at high shots, `=1` in exact mode.

**RB (H):** noiseless ⇒ survival `=1` for all lengths, fit `p=1`, `averageError=0`. (A depolarising channel from Section 5, when available, must give `r ≈ p_depol·(d−1)/d` — deferred.)

**XEB (I):** noiseless random circuit ⇒ `F_XEB ≈ 1` (±statistical). Feeding uniform-random outcomes ⇒ `F_XEB ≈ 0`.

Use generous epsilons for sampled tests; add exact/deterministic modes (expectation values straight from the state vector) so the math is verified without shot noise.

---

## 7. Risks

1. **Amplitude-index endianness mismatch (highest risk).** §2 documents that `SimpleQuantumExecutionEnvironment`'s init loop (big-endian, `pw=nQubits-j-1`) and `calculateQubitStatesFromVector` (little-endian, `div=1<<i`) disagree on qubit↔bit mapping. Partial trace and tomography are exquisitely sensitive to this. **Mitigation:** write the convention-pinning test (Testing §6) before any reduction code; encode the discovered convention as a single documented constant/helper used everywhere.
2. **Eigensolver robustness.** Hand-rolled Jacobi can misbehave on near-degenerate or non-Hermitian-by-rounding inputs. Mitigation: symmetrise input (`(M+M†)/2`) before solving; matrices are tiny so add a sweep cap + assertion that off-diagonal norm shrank.
3. **`float`-backed `Complex`.** `Complex` stores `r,i` as `float` (`Complex.java:69`). Accumulated error over `mmul` of 8×8 matrices and over 3ⁿ tomography settings may exceed tight epsilons. Mitigation: do reconstruction math in `double` locally (don't round-trip through `Complex` more than necessary); loosen sampled-test epsilons.
4. **Exponential scaling of full tomography (3ⁿ / 4ⁿ settings).** Fine for ≤3 qubits, intractable beyond ~6. Mitigation: document the limit; expose `nQubits` guards; this is inherent, not a defect.
5. **Clifford/Section-1 coupling for RB.** If Section 1 slips, RB is blocked on a clean `S`. Mitigation: inline `Sdg`/`S` locally so RB can proceed independently.
6. **Minimal-dependency tension.** If users later want N-qubit tomography, the in-house Jacobi solver won't scale and an EJML/Commons-Math dependency becomes tempting. Decision recorded: stay dependency-free for now (all matrices ≤8×8); revisit only if/when large-register tomography is a real requirement.

---

## 8. Suggested Sequencing

1. **A** — linear-algebra primitives + the index-convention test (unblocks everything; ship with its own tests first).
2. **B** + **C** — Bloch + entanglement measures (pure white-box, immediately useful, feeds Section 11, fully testable with Bell/GHZ oracles). Highest value-to-effort.
3. **D** + **E** — Sampler + basis-rotation gates (shared foundation for protocols).
4. **F** — state tomography (linear inversion, then MLE). First protocol; validates the whole measurement pipeline.
5. **G** — process tomography, 1-qubit then 2-qubit.
6. **I** — XEB (small, mostly reuses Sampler + exact probabilities).
7. **H** — RB last (depends on Section 1 Cliffords; only fully meaningful once Section 5 noise exists).

Deliver 1–2 as a self-contained first PR (no protocol/sampling machinery, no cross-section blockers); 3–7 as follow-ups.
