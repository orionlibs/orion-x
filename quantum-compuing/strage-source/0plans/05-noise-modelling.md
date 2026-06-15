# Section 5 — Error and Noise Modelling: Implementation Plan

## 1. Overview & Goals

Today Strange simulates **ideal, noiseless** unitary evolution: a pure state
vector `Complex[]` of size `2^n` evolved by `SimpleQuantumExecutionEnvironment`
(via `Computations.calculateNewState`). Real devices are noisy. This section
adds:

1. A `NoiseModel` abstraction that attaches **error channels** (per-gate /
   per-qubit / idle) to a `Program`.
2. **Quantum channels as first-class citizens** alongside unitary gates, defined
   by their **Kraus operators**: depolarizing, amplitude-damping, phase-damping,
   bit-flip (plus phase-flip, bit-phase-flip).
3. Two noisy execution environments implementing `QuantumExecutionEnvironment`:
   - **`TrajectoryQuantumExecutionEnvironment`** — Monte-Carlo wavefunction.
     Reuses the existing pure-state engine; stochastically picks one Kraus
     operator per channel per shot; averages over many trajectories. Memory
     `O(2^n)`, cost `O(shots · circuit)`. Approximate (statistical) but scales
     to the same qubit count as the current simulator.
   - **`DensityMatrixQuantumExecutionEnvironment`** — evolves the full density
     matrix `ρ` (`2^n × 2^n`). Applies each Kraus channel exactly as
     `ρ → Σ_k E_k ρ E_k†`. Exact (deterministic) but `O(2^{2n})` memory and
     `O(2^{3n})` per dense QuantumStep → practical to ~10–12 qubits.
4. **Readout (SPAM) error**: a per-qubit `2×2` mis-classification matrix applied
   at measurement / sampling time.
5. **T1/T2 relaxation** during idle qubits, mapped to amplitude-damping +
   phase-damping channels parameterised by idle duration.
6. **Crosstalk**: always-on nearest-neighbour ZZ coupling applied as a coherent
   correction per QuantumStep.
7. **Error-mitigation post-processors**: zero-noise extrapolation (ZNE),
   probabilistic error cancellation (PEC), and symmetry verification /
   post-selection — all implemented as wrappers around a noisy
   `QuantumExecutionEnvironment`, not as core engine changes.

Design constraints that the plan respects:

- **Do not modify** core semantics of `SimpleQuantumExecutionEnvironment`. Noisy
  environments are new classes implementing the same interface, so they are
  drop-in replacements (consistent with the `ideas.md` §6 promise).
- Reuse `Complex` arithmetic (`add`, `mul`, `abssqr`, `conjugateTranspose`,
  `mmul`, `tensor`, `identityMatrix`) — no new linear-algebra library.
- Reuse the existing QuantumStep engine in the trajectory path so we inherit all gate
  handling (single/two/three-qubit, blocks, swaps, oracles, permutations).

All new files live under `org/redfx/strange/noise/` (the package
`org.redfx.strange.noise`), except the two environments which live under the
existing `org/redfx/strange/local/` package alongside
`SimpleQuantumExecutionEnvironment`.

---

## 2. Current Simulator Analysis (cited)

State representation and evolution, from the real code:

- **State vector** — `SimpleQuantumExecutionEnvironment.runProgram(Program p)`
  (`local/SimpleQuantumExecutionEnvironment.java:65`) builds
  `int dim = 1 << nQubits; Complex[] probs = new Complex[dim];`
  (lines 72–74), seeds it from `p.getInitialAlphas()`, then for each decomposed
  QuantumStep calls `probs = applyStep(step, probs, qubit)` (line 108). The variable is
  misleadingly named `probs` but it is the **amplitude vector** (entries are
  complex amplitudes; probabilities are `abssqr()`).
- **Step application** — `applyStep` (line 145) special-cases
  `ProbabilitiesGate` and `PermutationGate`, otherwise delegates to
  `Computations.calculateNewState(gates, vector, qubits.length)` (line 161).
- **Core matrix-vector engine** — `Computations.calculateNewState`
  (`local/Computations.java:450`) → `getNextProbability` →
  `getNextProbability2` (line 479). For a single full-width gate it does the
  dense multiply
  `answer[i] = Σ_j matrix[i][j] · v[j]` (lines 560–564). For partial gates it
  tensor-blocks recursively. `getAllGates` (line 585) pads a QuantumStep to full width
  with `Identity` gates per qubit lane.
- **Measurement / sampling** — `Result.measureSystem()` (`Result.java:188`)
  samples one basis state by walking the cumulative distribution of
  `probability[i].abssqr()` against `Math.random()`, then sets each qubit's
  measured bit by `sel % 2` / `sel /= 2` (lines 200–211). Per-qubit marginals
  come from `calculateQubitStatesFromVector` (`Result.java:164`,
  `Computations.java:680`, and `SimpleQuantumExecutionEnvironment:198`).
- **Mid-circuit measurement** — `Computations.doImmediateMeasurement`
  (line 707) already does a stochastic projective collapse with renormalisation
  (`vector[i].mul(1/Math.sqrt(p[pick]))`). This is the **exact pattern** the
  trajectory Kraus-application QuantumStep will mimic.
- **Complex utilities available** (`Complex.java`):
  `mul`, `add`, `min`, `mul(double)`, `abssqr()` (line 170),
  `Complex.identityMatrix(dim)` (180), `tensor` (201), `mmul`/`slowmmul`
  (230/241), `conjugateTranspose` (269). Note `Complex` stores `float r, i`
  (line 69) — single precision; relevant to tolerances in tests.

Implications:

- The **trajectory** environment can literally re-run `runProgram` logic per
  shot, interleaving a stochastic Kraus QuantumStep after each gate QuantumStep — no new
  linear-algebra. It reuses `calculateNewState` for unitaries and a
  `doImmediateMeasurement`-style collapse for the chosen Kraus branch.
- The **density-matrix** environment cannot reuse the vector engine directly; it
  needs ρ as `Complex[][]` and applies a QuantumStep's full unitary `U` as
  `ρ → U ρ U†` plus channels `ρ → Σ E_k ρ E_k†`. We can still obtain a QuantumStep's
  full `U` from `Computations.calculateStepMatrix(gates, nQubits, qee)`
  (`Computations.java:68`), which already tensors a QuantumStep into a full
  `2^n × 2^n` matrix. (Permutation-containing decomposed QuantumSteps must be handled;
  see §6 caveat.)

---

## 3. Detailed Work Items

Ordered. Each item gives the file path(s), the type signatures, and code
sketches. Sketches are illustrative Java consistent with the existing style
(verbose, `Complex[][]`, no external libs).

### 3.1 Kraus / `QuantumChannel` types (foundation)

**Files:**
- `org/redfx/strange/noise/QuantumChannel.java`
- `org/redfx/strange/noise/Channels.java` (factory of standard channels)
- `org/redfx/strange/noise/KrausApplication.java` (vector + matrix helpers)

A `QuantumChannel` is a CPTP map represented by a list of Kraus operators
`{E_k}` acting on a fixed number of qubits, satisfying `Σ_k E_k† E_k = I`.

```java
package org.redfx.strange.noise;

import Complex;
import java.util.List;

/** A completely-positive trace-preserving map given by Kraus operators. */
public interface QuantumChannel {

    /** Number of qubits this channel acts on (1 for all single-qubit channels). */
    int getSize();

    /**
     * Kraus operators {E_k}. Each is a (2^size x 2^size) Complex matrix.
     * Must satisfy sum_k E_k^dagger E_k = I (validated in tests).
     */
    List<Complex[][]> getKrausOperators();

    /** Human-readable name, used by mitigation/logging. */
    String getName();
}
```

A concrete base that stores a fixed operator list:

```java
public class KrausChannel implements QuantumChannel {
    private final String name;
    private final int size;
    private final List<Complex[][]> ops;
    public KrausChannel(String name, int size, List<Complex[][]> ops) { ... }
    // getters; constructor optionally asserts completeness via Channels.isTracePreserving(ops)
}
```

**Standard single-qubit channels** (`Channels.java`). Matrices below are the
exact Kraus sets the implementation must produce. `p` is the error
probability; `γ` the damping parameter; entries are `new Complex(re, im)`.

- **Bit-flip** (X with prob p):
  `E0 = √(1-p)·I`, `E1 = √p·X`
  ```
  E0 = [[√(1-p),0],[0,√(1-p)]]   E1 = [[0,√p],[√p,0]]
  ```
- **Phase-flip** (Z with prob p):
  `E0 = √(1-p)·I`, `E1 = √p·Z = [[√p,0],[0,-√p]]`
- **Bit-phase-flip** (Y with prob p):
  `E0 = √(1-p)·I`, `E1 = √p·Y = [[0,-i√p],[i√p,0]]`
- **Depolarizing** (with prob p replace by maximally mixed). Standard 4-Kraus
  form:
  ```
  E0 = √(1 - 3p/4)·I
  E1 = √(p/4)·X
  E2 = √(p/4)·Y
  E3 = √(p/4)·Z
  ```
  (Equivalent "uniform Pauli" form with weight p/3 on each Pauli and
  `√(1-p)` on I is also acceptable; pick the `3p/4` convention and document it.
  Channel: `ρ → (1-p)ρ + p·I/2`.)
- **Amplitude-damping** (T1-type, parameter γ = prob of |1⟩→|0⟩ decay):
  ```
  E0 = [[1, 0],
        [0, √(1-γ)]]
  E1 = [[0, √γ],
        [0, 0]]
  ```
- **Phase-damping** (T2-type pure dephasing, parameter λ):
  ```
  E0 = [[1, 0],
        [0, √(1-λ)]]
  E1 = [[0, 0],
        [0, √λ]]
  ```
  (Equivalent two-Kraus alternative
  `E0=√(1-λ/2)I, E1=√(λ/2)Z` may be used; keep one convention.)

Factory signatures:

```java
public final class Channels {
    public static QuantumChannel bitFlip(double p);
    public static QuantumChannel phaseFlip(double p);
    public static QuantumChannel bitPhaseFlip(double p);
    public static QuantumChannel depolarizing(double p);
    public static QuantumChannel amplitudeDamping(double gamma);
    public static QuantumChannel phaseDamping(double lambda);
    /** Compose two channels by Kraus-product: ops = {A_i B_j}. */
    public static QuantumChannel compose(QuantumChannel a, QuantumChannel b);
    /** Sum_k E_k^dagger E_k ?= I within eps. */
    public static boolean isTracePreserving(List<Complex[][]> ops, double eps);
}
```

`KrausApplication` provides the two ways to apply Kraus operators:

```java
public final class KrausApplication {

    /** Density-matrix path: rho -> sum_k E_k rho E_k^dagger, E_k acting on `qubit`. */
    public static Complex[][] applyToDensity(
            Complex[][] rho, QuantumChannel ch, int qubit, int nQubits);

    /**
     * Trajectory path: given a normalised state vector, pick ONE Kraus branch
     * stochastically and return the renormalised post-state.
     * Probability of branch k = || E_k |psi> ||^2 (sums to 1 because TP).
     */
    public static Complex[] sampleKrausBranch(
            Complex[] state, QuantumChannel ch, int qubit, int nQubits, java.util.Random rng);
}
```

`sampleKrausBranch` mirrors `Computations.doImmediateMeasurement`
(`Computations.java:707`): expand the 1-qubit `E_k` into the full operator on
the target lane (tensor with identities, or apply E_k only across the index bit
`b = 1<<qubit` to avoid materialising the full matrix), compute each branch's
unnormalised norm `n_k = ||E_k|ψ⟩||²`, draw `r = rng.nextDouble()`, select `k`
by cumulative `n_k`, and renormalise by `1/√(n_k)`. For single-qubit channels
the per-amplitude application is:
```
for each basis index i:
    bit = (i >> qubit) & 1
    pair index i0 (bit=0), i1 (bit=1)
    [out_i0, out_i1] = E_k · [state_i0, state_i1]
```

### 3.2 `NoiseModel` API

**File:** `org/redfx/strange/noise/NoiseModel.java`
(+ builder `NoiseModelBuilder.java`).

A `NoiseModel` answers: "which channels apply after a given gate / on a given
qubit / during idle time of given duration?" It is consulted by both noisy
environments.

```java
package org.redfx.strange.noise;

import org.redfx.strange.Gate;
import java.util.List;

public interface NoiseModel {

    /** Channels to apply on `qubit` immediately AFTER gate `g` executes. */
    List<TargetedChannel> afterGate(Gate g, int qubit);

    /** Idle relaxation channel on `qubit` for an idle of `duration` (ns). May be empty. */
    List<TargetedChannel> idle(int qubit, double durationNs);

    /** Per-qubit readout (SPAM) confusion matrix; 2x2 row-stochastic, P(measured|prepared). */
    double[][] readoutMatrix(int qubit);     // {{P(0|0),P(0|1)},{P(1|0),P(1|1)}}

    /** Optional coherent crosstalk on a QuantumStep (e.g. ZZ). Return null/empty if none. */
    List<CrosstalkTerm> crosstalk();

    /** Gate durations (ns), used to derive idle windows for T1/T2. */
    double gateDurationNs(Gate g);

    /** T1, T2 (ns) for a qubit; used by default idle() implementation. */
    double t1(int qubit);
    double t2(int qubit);
}
```

Supporting records:

```java
public record TargetedChannel(QuantumChannel channel, int qubit) {}
public record CrosstalkTerm(int qubitA, int qubitB, double zzStrength /* rad/ns */) {}
```

A concrete, configurable implementation:

```java
public class StandardNoiseModel implements NoiseModel {
    // per-gate depolarizing rates (1q vs 2q), readout matrices, T1/T2 maps,
    // gate durations, crosstalk graph. Built via NoiseModelBuilder.
}
```

`NoiseModelBuilder` fluent API:

```java
NoiseModel nm = NoiseModel.builder(nQubits)
    .oneQubitGateError(0.001)          // depolarizing p after each 1q gate
    .twoQubitGateError(0.01)           // depolarizing p after each 2q gate
    .t1(/*qubit*/ q, /*ns*/ 80_000).t2(q, 60_000)
    .gateDuration(/*1q ns*/ 35).twoQubitGateDuration(300)
    .readoutError(q, /*p(1|0)*/0.02, /*p(0|1)*/0.04)
    .zzCrosstalk(q, q+1, /*rad/ns*/ 1e-5)
    .build();
```

`NoiseModel.builder(int)` is a static factory returning `NoiseModelBuilder`.
`afterGate` default: for a gate with `g.getSize()==1` return one
`depolarizing(oneQubitGateError)` on the qubit; for size 2 return depolarizing
on each affected qubit (`g.getAffectedQubitIndexes()`).

### 3.3 Trajectory simulator (reuses existing engine)

**File:** `org/redfx/strange/local/TrajectoryQuantumExecutionEnvironment.java`,
package `org.redfx.strange.local`.

```java
public class TrajectoryQuantumExecutionEnvironment implements QuantumExecutionEnvironment {
    private final NoiseModel noise;
    private final int shots;
    private final java.util.Random rng;

    public TrajectoryQuantumExecutionEnvironment(NoiseModel noise, int shots) { ... }

    @Override public Result runProgram(Program p) { ... }
    @Override public void runProgram(Program p, java.util.function.Consumer<Result> r) { ... }
}
```

Algorithm:

1. Decompose QuantumSteps exactly as the simple env does (reuse the same code path:
   `p.getDecomposedSteps()` / `Computations.decomposeStep`,
   `SimpleQuantumExecutionEnvironment.java:89–97`).
2. For each shot `s` in `[0, shots)`:
   a. Build the initial amplitude vector identically to
      `SimpleQuantumExecutionEnvironment.runProgram` (lines 72–88).
   b. For each decomposed QuantumStep:
      - Apply the unitary QuantumStep via the **existing** engine. Factor the body of
        `applyStep` into a reusable static helper or call
        `Computations.calculateNewState(step.getGates(), vector, nQubits)`
        directly (handling the `ProbabilitiesGate` / `PermutationGate`
        special-cases the same way `applyStep` does,
        `SimpleQuantumExecutionEnvironment.java:148–161`).
      - For each gate `g` in the QuantumStep and each affected qubit `q`, for each
        `TargetedChannel tc : noise.afterGate(g, q)`:
        `vector = KrausApplication.sampleKrausBranch(vector, tc.channel(), tc.qubit(), nQubits, rng);`
      - For each **idle** qubit in this QuantumStep (qubits not touched by any gate),
        apply `noise.idle(q, noise.gateDurationNs(stepWidthGate))` channels the
        same way (see §6 for idle-duration derivation).
      - Apply crosstalk (§6.3) as a coherent ZZ QuantumStep if present.
   c. After the last QuantumStep, **sample one bitstring** from `|vector|²` exactly as
      `Result.measureSystem` does (`Result.java:188`), then apply **readout
      error** (§5) to flip bits per the confusion matrix. Accumulate the
      bitstring into a histogram `long[] counts` of length `2^n`.
3. Build the returned `Result`: synthesise a `Complex[] probability` vector
   whose `abssqr()` equals `counts[i]/shots` (store amplitude
   `new Complex(Math.sqrt(counts[i]/shots), 0)`), set it via
   `result.setIntermediateProbability(0, probs)`, and `result.measureSystem()`.
   This keeps the `Result` contract: downstream marginals
   (`calculateQubitStatesFromVector`) and sampling work unchanged.

   > Note: the returned `Result.probability` is the **measured/empirical**
   > distribution (mixed-state diagonal), not coherent amplitudes — this is the
   > correct observable for a noisy run. Document that intermediate-probability
   > snapshots reflect the ensemble average, not a single trajectory.

Tradeoffs: memory `O(2^n)` (one trajectory at a time); cost
`O(shots · QuantumSteps · 2^n)` for the gate multiplies (same constant as the simple
env, ×shots). Statistical error `~1/√shots`. No off-diagonal coherence stored,
but ensemble-averaged observables and bitstring statistics are correct.

### 3.4 Density-matrix simulator (exact)

**File:**
`org/redfx/strange/local/DensityMatrixQuantumExecutionEnvironment.java`.

```java
public class DensityMatrixQuantumExecutionEnvironment implements QuantumExecutionEnvironment {
    private final NoiseModel noise;       // may be null => ideal, just exact ρ
    public DensityMatrixQuantumExecutionEnvironment(NoiseModel noise) { ... }
    @Override public Result runProgram(Program p) { ... }
    @Override public void runProgram(Program p, Consumer<Result> r) { ... }
}
```

State: `Complex[][] rho` of size `2^n × 2^n`. Init: from the initial amplitude
vector `|ψ0⟩` (same construction as the simple env), set
`rho = |ψ0⟩⟨ψ0|`, i.e. `rho[i][j] = ψ0[i] · conj(ψ0[j])`.

Per decomposed QuantumStep:

1. Compute the QuantumStep's full unitary
   `Complex[][] U = Computations.calculateStepMatrix(step.getGates(), nQubits, this)`
   (`Computations.java:68`). Apply coherently:
   `rho = U · rho · U†` using `Complex.mmul` and `Complex.conjugateTranspose`
   (`Complex.java:230, 269`).
   - **Permutation/swap caveat:** decomposed QuantumSteps may contain a
     `PermutationGate` (which `calculateStepMatrix` rejects,
     `Computations.java:104`). For the density env, prefer to iterate over the
     **original** (non-decomposed) QuantumSteps and build `U` from a
     permutation-tolerant matrix builder, OR handle a lone `PermutationGate`
     QuantumStep by permuting `rho`'s rows and columns directly via
     `Computations.permutateVector`-style index swaps applied to both indices.
     Concretely: for a `PermutationGate(a,b)` QuantumStep,
     `rho'[i][j] = rho[perm(i)][perm(j)]` where `perm` swaps bits a,b
     (`Computations.swapBits`, line 750). Lone `Swap` similarly.
2. Apply noise channels for the QuantumStep: for each gate `g`, affected qubit `q`, and
   `tc : noise.afterGate(g, q)`:
   `rho = KrausApplication.applyToDensity(rho, tc.channel(), tc.qubit(), nQubits);`
3. Apply idle relaxation channels on idle qubits and crosstalk ZZ (as a unitary
   `exp(-i Σ θ ZZ)` applied coherently, §6.3), same per-step.

`applyToDensity` for a single-qubit channel on qubit `q`: rather than
materialising full `2^n` Kraus matrices, apply each `E_k` (2×2) on the `q`-lane
of both index axes:
```
new_rho = 0
for each E_k:
    tmp  = E_k applied on the q-lane (left)   of rho     // 2x2 mixing of index bit q on rows
    out += tmp applied on the q-lane (right) by E_k^dagger // on columns
```
This is `O(K · 2^{2n})` per channel (K = #Kraus, usually 2–4), avoiding the
`O(2^{3n})` of full matrix triple-products.

Output: the measurement distribution is the **diagonal of ρ**:
`p[i] = rho[i][i].r` (imaginary part ≈ 0). Build the returned `Result` the same
way as §3.3 (store `new Complex(Math.sqrt(p[i]),0)` as amplitude), then apply
readout error to the diagonal before sampling.

Tradeoffs: **exact** for arbitrary mixed states and coherences; memory
`O(2^{2n})` `Complex` objects (each is two floats + object header → enforce a
guard, e.g. refuse `nQubits > 12` unless overridden); per dense unitary QuantumStep
`O(2^{3n})` via `mmul`. Use sparse/lane-wise application for channels and
permutations to keep the common case tractable.

### 3.5 Readout (SPAM) error

**File:** folded into the noise package; applied at sampling time.

Per-qubit confusion matrix `M_q = [[P(0|0), P(0|1)], [P(1|0), P(1|1)]]`
(row = measured outcome, column = true state), from `noise.readoutMatrix(q)`.

- **Trajectory env:** after sampling a true bitstring, for each qubit flip the
  bit with the appropriate conditional probability:
  if true bit = 0, with prob `P(1|0)` report 1; if true bit = 1, with prob
  `P(0|1)` report 0. Draw one `rng.nextDouble()` per qubit.
- **Density env:** transform the diagonal distribution. The full-system readout
  operator is the tensor product `R = ⊗_q M_q` acting on the probability vector:
  `p_meas = R · p_true` where `R[out][true] = Π_q M_q[out_q][true_q]`. Compute
  per-qubit (apply each `M_q` along its index bit) to avoid materialising the
  `2^n × 2^n` `R`.

`assertReadoutRowStochastic` test helper checks each column sums to 1.

### 3.6 T1/T2 relaxation and crosstalk

**Idle window derivation.** A qubit is *idle* during a QuantumStep if no gate in that
step touches it. The idle duration is the QuantumStep's wall-clock duration, taken as
the max gate duration in the QuantumStep: `Δt = max_g noise.gateDurationNs(g)`. (If a
step is all-idle, use the configured default 1q duration.)

**T1 → amplitude damping.** Over idle time `Δt` with relaxation time `T1`:
`γ = 1 - exp(-Δt / T1)`. Apply `Channels.amplitudeDamping(γ)` on the idle qubit.

**T2 → phase damping.** T2 includes both T1 decay and pure dephasing. Pure
dephasing rate: `1/T_φ = 1/T2 - 1/(2 T1)`. Over `Δt`:
`λ = 1 - exp(-2 Δt / T_φ)`  (phase-damping parameter such that off-diagonal
coherence decays by `exp(-Δt/T_φ)` after also accounting for amplitude damping).
Apply `Channels.phaseDamping(λ)`. Combine as
`Channels.compose(phaseDamping(λ), amplitudeDamping(γ))` so the idle channel is a
single CPTP map. Guard `T_φ > 0` (i.e. `T2 ≤ 2 T1`); if violated, clamp and warn.

`StandardNoiseModel.idle(q, Δt)` returns
`[ new TargetedChannel(compose(phaseDamping(λ), amplitudeDamping(γ)), q) ]`
computed from `t1(q)`, `t2(q)`, `Δt`.

**Crosstalk (nearest-neighbour ZZ).** For each `CrosstalkTerm(a,b,ξ)` in
`noise.crosstalk()`, the always-on coupling over a QuantumStep of duration `Δt`
contributes the coherent unitary `U_zz = exp(-i ξ Δt Z_a Z_b / 2)`, which is
diagonal in the computational basis:
`U_zz[i][i] = exp(-i ξ Δt/2 · s_a s_b)` where `s = +1` for bit 0 and `-1` for
bit 1 (eigenvalue of Z). Apply per QuantumStep:
- Trajectory env: multiply each amplitude `vector[i]` by the diagonal phase.
- Density env: `rho[i][j] *= phase(i) · conj(phase(j))`.
Implement as `Crosstalk.applyVector(...)` / `Crosstalk.applyDensity(...)`.

### 3.7 Zero-Noise Extrapolation (ZNE) — wrapper / post-processor

**File:** `org/redfx/strange/noise/mitigation/ZeroNoiseExtrapolation.java`.

ZNE runs the circuit at several **noise scale factors** `λ ∈ {1, 3, 5, ...}`,
measures an observable expectation `⟨O⟩(λ)`, then extrapolates to `λ → 0`.

Noise scaling by **unitary folding** (gate-level, hardware-agnostic): replace
each gate `G` by `G (G† G)^k` so the *logical* circuit is unchanged but it
experiences `(2k+1)×` the noise. `G†` is obtained from a gate's matrix via
`Complex.conjugateTranspose(G.getMatrix())` wrapped in an `Oracle`
(`Gate.oracle(Complex[][])`, `Gate.java:99`) — i.e. build a folded `Program`.

```java
public class ZeroNoiseExtrapolation {
    public ZeroNoiseExtrapolation(QuantumExecutionEnvironment noisyEnv);
    /** scaleFactors e.g. {1,3,5}; observable maps a measured bitstring/diagonal to a double. */
    public double mitigate(Program p, int[] scaleFactors, Observable obs, ExtrapType type);
    public enum ExtrapType { LINEAR, RICHARDSON, EXPONENTIAL }
}
```

Steps: for each `s` in `scaleFactors`, build `foldedProgram(p, s)` (fold every
gate to multiply noise ≈ s×), run on `noisyEnv`, compute `y_s = ⟨O⟩`. Fit
`y(λ)` and evaluate at `λ=0`:
- LINEAR: least-squares line through `(s, y_s)`.
- RICHARDSON: exact polynomial extrapolation through all points to `λ=0`.
- EXPONENTIAL: fit `y = a + b·exp(-c λ)`.

`Observable` is a functional interface
`double value(Complex[] probabilityVector)` (e.g. parity / `⟨Z⟩`), evaluated on
the `Result.getProbability()` of each run.

### 3.8 Probabilistic Error Cancellation (PEC) — wrapper

**File:** `org/redfx/strange/noise/mitigation/ProbabilisticErrorCancellation.java`.

PEC writes the inverse of each noisy gate's channel as a **quasi-probability**
combination of *implementable* noisy operations:
`G_ideal = Σ_i a_i  O_i`, with `Σ_i |a_i| = γ ≥ 1` and `sign(a_i)` carried as a
parity. For each shot, sample operation `O_i` with prob `|a_i|/γ`, run it,
accumulate the measured observable times `γ · sign(a_i)`; the average is an
unbiased estimate of the noiseless expectation, with variance inflated by `γ²`.

```java
public class ProbabilisticErrorCancellation {
    public ProbabilisticErrorCancellation(QuantumExecutionEnvironment noisyEnv,
                                          NoiseModel model);
    /** Build per-gate quasi-prob decompositions from the model's channels. */
    public double mitigate(Program p, Observable obs, int shots);
}
```

Implementation notes:
- For a depolarizing channel with parameter `p` on one qubit, the inverse map's
  quasi-probability decomposition over the Pauli basis `{I,X,Y,Z}` has known
  coefficients; compute them from `p` (closed form:
  `a_I = (1 + 3(1-p))/(4(1-p))`-style; derive from inverting the Pauli-transfer
  matrix `diag(1, 1-p, 1-p, 1-p)` of the channel and re-expressing as a
  quasi-prob over noisy-Pauli insertions). Store as a `QuasiProbDecomposition`
  (list of `(coeff, Gate insertion)`).
- A `PauliTransferMatrix` helper (`org/redfx/strange/noise/PauliTransferMatrix.java`)
  converts a `QuantumChannel`'s Kraus set to its 4×4 (single-qubit) PTM; PEC
  inverts it (real 4×4) to get coefficients.
- Per shot: walk the program, at each noisy gate insert a sampled Pauli with the
  decomposition's probabilities, track running sign and `γ_total = Π γ_gate`.
- Estimator = mean over shots of `sign · γ_total · obs(result)`.

PEC is exact in expectation (unlike ZNE) but costs sampling overhead `γ_total²`.

### 3.9 Symmetry verification / post-selection — wrapper

**File:** `org/redfx/strange/noise/mitigation/SymmetryVerification.java`.

For circuits with a known symmetry (e.g. fixed total parity / particle number),
add **ancilla syndrome measurements** (or compute the symmetry from the measured
bitstring) and **discard** shots that violate the symmetry.

```java
public class SymmetryVerification {
    public SymmetryVerification(QuantumExecutionEnvironment noisyEnv);
    /** predicate over the measured bitstring; keep only passing shots. */
    public Result postSelect(Program p, int shots, java.util.function.IntPredicate symmetryOk);
    /** convenience: parity check over a qubit subset == expectedParity. */
    public Result postSelectParity(Program p, int shots, int[] qubits, boolean expectedParity);
}
```

Implementation: run `shots` single-shot trajectories (reuse the trajectory env),
collect measured bitstrings, drop those failing `symmetryOk`, rebuild the
histogram and `Result.probability` from the surviving shots. Report the
acceptance rate. Works directly on the trajectory env's per-shot bitstrings;
for the density env, post-selection is implemented by projecting ρ onto the
symmetry subspace `ρ → PρP / Tr(PρP)` before reading the diagonal.

---

## 4. Testing Strategy

Add tests under the existing test tree (mirror current test packages,
`org.redfx.strange.test` style). Account for `Complex` being **single
precision** (`float`) — use `eps ≈ 1e-5` for amplitude comparisons,
`1e-4`–`1e-3` for statistical/trajectory checks. Reuse `QuantumAssert`-style
helpers if added (`ideas.md` §10), else local helpers.

Channel correctness (unit):
- `isTracePreserving` holds for every standard channel across `p, γ, λ ∈ [0,1]`.
- Depolarizing with `p=1` on `|0⟩` → `ρ = I/2` (density env): assert
  `rho == [[0.5,0],[0,0.5]]` and off-diagonals 0.
- Amplitude damping with `γ=1` on `|1⟩` → `|0⟩` deterministically (both envs).
- Phase damping leaves diagonal unchanged, shrinks off-diagonal of `|+⟩` by
  `√(1-λ)` (density env): prepare H|0⟩, apply phase-damping, check `rho[0][1].r`.
- Bit-flip `p=0.5` on `|0⟩` → diagonal `(0.5, 0.5)`.

Trajectory vs density agreement:
- For a fixed small circuit (1–3 qubits) and a fixed noise model, the
  trajectory env with large `shots` (e.g. 50k) must match the density env's
  diagonal distribution within `~3/√shots`. Drive `rng` with a fixed seed.

Fidelity-decay curves (integration):
- Single qubit, repeated identity/idle QuantumSteps under amplitude damping: measured
  `P(|1⟩)` must follow `exp(-t/T1)`; fit decay constant and assert ≈ `T1`.
- Under phase damping, `⟨X⟩` of `|+⟩` decays as `exp(-t/T_φ)`; assert fitted
  constant ≈ configured `T_φ`.
- Two-qubit depolarizing per CNOT: state fidelity vs ideal decays monotonically
  with depth; assert monotone and matches `(1-p)^depth` trend.

Readout error:
- Prepare deterministic `|0...0⟩`, set `P(1|0)=0.1` on one qubit, run many
  shots, assert measured-1 frequency ≈ 0.1 within statistics.
- Row/column stochasticity assertions on every configured `M_q`.

Crosstalk:
- Two-qubit ZZ on `|+ +⟩` produces the expected relative phase / correlation;
  compare to closed-form `U_zz` applied analytically.

Mitigation:
- ZNE: construct a depolarizing-only model with known analytic `⟨O⟩(λ)` (linear
  in `p`); assert extrapolated `λ=0` value ≈ noiseless within tolerance, and
  that error at `λ=0` < error at `λ=1`.
- PEC: on a single noisy gate with depolarizing `p`, assert the PEC estimator of
  `⟨Z⟩` is unbiased (mean over many shots ≈ noiseless) and that variance grows
  with `γ²`.
- Symmetry verification: inject bit-flip noise into a parity-conserving circuit;
  assert post-selected distribution is closer to ideal than the raw one, and
  acceptance rate is reported.

Regression / non-interference:
- `DensityMatrixQuantumExecutionEnvironment` with `noise == null` reproduces
  `SimpleQuantumExecutionEnvironment` results (diagonal of ρ == `|amplitude|²`)
  for Bell, GHZ, QFT, and a small Grover circuit — confirms the U·ρ·U† path and
  permutation handling are correct.

---

## 5. Risks & Mitigations

- **Density-matrix memory blow-up (`O(2^{2n})`).** A 12-qubit ρ is `2^24 ≈ 16.7M`
  `Complex` objects → multi-GB with boxed objects. Mitigations: hard guard
  (`nQubits > 12` throws unless `allowLargeDensity` set); document the limit;
  use lane-wise channel/permutation application (no full Kraus tensor); consider
  a future flat `float[]` backing store instead of `Complex[][]` for ρ.
- **Float precision** (`Complex` uses `float`). Triple products `U ρ U†` and
  long idle chains accumulate error. Mitigation: loose epsilons in tests;
  optionally accumulate in `double` inside hot loops; flag a possible future
  `double`-backed Complex (tracked in `ideas.md` §13).
- **Permutation gates in decomposed QuantumSteps** break `calculateStepMatrix`
  (`Computations.java:104` throws). Mitigation: density env handles lone
  `PermutationGate`/`Swap` QuantumSteps by direct index-bit row/column swaps on ρ
  (§3.4); add explicit tests for circuits that decompose into permutations
  (non-adjacent two-qubit gates, Toffoli).
- **Trajectory statistical noise.** Observables converge as `1/√shots`; document
  required shot counts; seedable `Random` for reproducible tests.
- **Channel convention drift.** Multiple valid Kraus forms exist (depolarizing
  `3p/4` vs `p/3`; phase-damping 2-Kraus vs alternative). Lock one convention in
  `Channels`, assert trace preservation in tests, document it.
- **T2 ≤ 2·T1 constraint.** `T_φ` undefined otherwise; clamp and warn.
- **PEC PTM inversion** can be ill-conditioned for near-identity channels and
  amplifies variance (`γ²`). Mitigation: cap `γ_total`, warn when large, prefer
  ZNE for high-`γ` regimes.
- **`Result` contract reinterpretation.** Noisy `Result.probability` is the
  measured/mixed diagonal, not coherent amplitudes. Mitigation: document clearly
  and keep `Result` API unchanged so existing consumers still work.

---

## 6. Suggested Sequencing

1. **Channels foundation** — `QuantumChannel`, `KrausChannel`, `Channels`
   factory, `KrausApplication` (vector + density), trace-preservation tests.
   (§3.1)
2. **`NoiseModel` API** + `StandardNoiseModel` + `NoiseModelBuilder`, with
   default depolarizing `afterGate`. (§3.2)
3. **Trajectory simulator** — reuses existing engine; quickest path to a working
   noisy backend; validate against analytic single-qubit decay. (§3.3)
4. **Readout error** in the trajectory path. (§3.5)
5. **T1/T2 idle + crosstalk** in the trajectory path; fidelity-decay tests.
   (§3.6)
6. **Density-matrix simulator** — exact reference; cross-validate trajectory
   against it; handle permutation/swap QuantumSteps; regression vs simple env. (§3.4)
7. **Symmetry verification / post-selection** — small, builds on trajectory
   bitstrings. (§3.9)
8. **ZNE** — folding + extrapolation; needs only a noisy env + observable. (§3.7)
9. **PEC** — `PauliTransferMatrix`, quasi-prob sampling; most involved, do last.
   (§3.8)

Each stage is independently testable and leaves the existing
`SimpleQuantumExecutionEnvironment` untouched.
