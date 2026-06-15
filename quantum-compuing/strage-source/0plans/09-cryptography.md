# Section 9 — Quantum Cryptography Primitives (Implementation Plan)

Target library: **Strange** (`org.redfx.strange`), flat source layout at repo root.
New package: **`org.redfx.strange.crypto`** in a new `crypto/` directory.

This plan is self-contained and code-accurate against the existing Strange API. (`1.md`
was not present in the working tree at planning time; the plan follows the Section 9
brief and the actual source.)

---

## 1. Overview & goals

Implement a self-contained suite of quantum-cryptographic primitives, all driven by the
existing Strange simulator (`SimpleQuantumExecutionEnvironment`) through ordinary
`Program`/`Step`/`Gate` objects. Deliverables:

1. **QRNG** — extend the existing single-bit `Classic.randomBit()` to a streaming,
   N-bit / N-byte generator plus a **NIST STS-style** statistical test harness
   (representative subset: monobit/frequency, block frequency, runs, longest-run,
   cumulative-sums).
2. **BB84** QKD — prepare/transmit/measure/sift/QBER-estimate, modeled with explicit
   Alice / Bob / Eve role classes and a simulated quantum **channel**.
3. **Eavesdropper** (intercept-resend) — pluggable into the channel; statistical
   detection via QBER (~25 % expected with full interception).
4. **B92** QKD — two-state protocol with non-orthogonal states.
5. **E91** QKD — entangled (Bell) pairs + **CHSH** inequality test for security.
6. **Quantum secret sharing** (design-level, with a concrete GHZ-based sketch).
7. **Quantum digital signatures** (information-theoretic, design-level sketch).

### Design principles

- **Every qubit exchange is a fresh single-qubit `Program`.** A party "prepares" a
  qubit by choosing the preparation gates; the carrier qubit is transmitted as a
  small immutable value object (the *classical description* of the prepared state, i.e.
  bit + basis), NOT as live simulator state. The receiver builds its own `Program`
  that re-prepares the state, appends its measurement-basis rotation, runs it, and
  measures. This faithfully models a one-qubit-at-a-time channel and keeps each
  simulation 1 qubit wide (except E91, which is 2 qubits wide).
  - Rationale: Strange's `Qubit.measure()` only returns a value the QEE already wrote
    into it from the run; there is no persistent live qubit you can hand to another
    party. Re-preparing the described state in the receiver's `Program` is the correct
    and simplest fidelity-preserving model. See §3 for the carrier object.
- **All randomness comes from the QRNG** (`QuantumRandom`), which itself is built on
  `Classic.randomBit()`. This makes the protocols genuinely quantum-random and lets the
  QRNG be tested independently.
- **Strict typing, no nulls across role boundaries; small immutable value records.**
- Mirror existing style: license header block, `package org.redfx.strange.crypto;`,
  Javadoc on public methods, `Program`/`Step`/`Gate` construction identical to
  `Classic.java`.

---

## 2. Building blocks available (cited)

From the existing source, confirmed by reading:

- **`org.redfx.strange.algorithm.Classic.randomBit()`** (`algorithm/Classic.java:67`):
  ```java
  Program program = new Program(1, new Step(new Hadamard(0)));
  Result result = qee.runProgram(program);
  int answer = result.getQubits()[0].measure();
  ```
  This is the canonical single-bit Hadamard QRNG; the QRNG work generalizes it. It also
  shows the `setQuantumExecutionEnvironment(QuantumExecutionEnvironment)` hook
  (`Classic.java:58`) for swapping the QEE — reuse the same pattern in crypto classes.

- **`Program(int nQubits, Step... steps)`** + `addStep(Step)` / `addSteps(Step...)`
  (`Program.java:73`, `:117`). Note `Program.ensureMeasuresafe` forbids putting a
  *superposition* gate (Hadamard/Cnot) after a `Measurement` on the same qubit
  (`Program.java:140`) — so always order steps as prepare → rotate → measure, never the
  reverse.
- **`Step(Gate... gates)`** and `Step(String name, Gate...)` (`Step.java:85`, `:95`);
  one gate per qubit per step.
- **`Result.getQubits()` → `Qubit[]`**, `Qubit.measure()` returns `0|1`
  (`Qubit.java:96`).
- **Gates** (all in `org.redfx.strange.gate`):
  - `Hadamard(idx)` — basis change / superposition (`gate/Hadamard.java`).
  - `X(idx)` — bit flip, encodes a logical `1` (`gate/X.java`).
  - `Z(idx)` — phase flip; with H gives the diagonal/Hadamard basis (`gate/Z.java`).
  - `Y(idx)` — used for some prep/measurement combos (`gate/Y.java`).
  - `Cnot(a,b)` — entangling gate for Bell-pair creation (`gate/Cnot.java`).
  - `RotationY(double theta, int idx)` — arbitrary single-qubit basis rotation, needed
    for E91 measurement angles (0°, 45°, 90°, 135°) (`gate/RotationY.java`,
    `extends Rotation`).
  - `Measurement(idx)` — explicit measurement gate (`gate/Measurement.java`); optional,
    since `Qubit.measure()` already reads the collapsed value after a run.
- **`org.redfx.strange.local.SimpleQuantumExecutionEnvironment`** — the default QEE.

### Basis / state encoding reference (used throughout)

Two BB84 bases:
- **Rectilinear `+`** (Z-basis): states `|0>` (bit 0), `|1>` (bit 1).
- **Diagonal `×`** (X-basis): states `|+>` (bit 0), `|−>` (bit 1).

Preparation circuit for (bit `b`, basis `B`) on qubit 0:
```
if (b == 1) addGate X(0)          // |0> -> |1>
if (B == DIAGONAL) addGate H(0)   // |0>/|1> -> |+>/|->
```
Measurement in basis `B`:
```
if (B == DIAGONAL) addGate H(0)   // rotate diagonal basis back to Z before measuring
measure qubit 0
```
This pair is exactly H·X composition and is the foundation for all of BB84/B92/Eve.

---

## 3. Common infrastructure (build first)

### 3.1 `crypto/QuantumRandom.java` — streaming QRNG
`org.redfx.strange.crypto.QuantumRandom`

Extends the single-bit pattern of `Classic.randomBit()`. Holds its own QEE reference
(default `new SimpleQuantumExecutionEnvironment()`), with a setter mirroring
`Classic.setQuantumExecutionEnvironment`.

API sketch:
```java
public final class QuantumRandom {
    private QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
    public void setQuantumExecutionEnvironment(QuantumExecutionEnvironment q) { this.qee = q; }

    public int nextBit();                 // one Hadamard measurement (== Classic.randomBit)
    public int[] nextBits(int n);         // n independent bits
    public byte[] nextBytes(int n);       // n bytes, MSB-first packing of 8*n bits
    public boolean nextBoolean();
    public int nextInt(int bound);        // rejection sampling over ceil(log2(bound)) bits
    public java.util.stream.IntStream bitStream();      // lazy infinite 0/1 stream
    public java.util.stream.Stream<byte[]> byteStream(int chunk); // streaming chunks
}
```
Implementation notes:
- `nextBit()` body is identical to `randomBit()`: a 1-qubit program with `Hadamard(0)`.
- For throughput, optionally add a **batched** variant `nextBitsBatched(int n)` that
  builds ONE `Program(n)` with a `Step` containing `Hadamard(i)` for all `i in [0,n)`,
  runs once, and measures all qubits. Document the simulator cost: state vector is
  `2^n`, so cap batch width (e.g. ≤ 16 qubits per program) and loop batches. Default
  public methods use the safe 1-qubit-at-a-time loop; batched is an opt-in fast path.
- No seeding API (true quantum randomness); but allow injecting a deterministic QEE in
  tests via the setter so the harness itself is testable.

### 3.2 `crypto/Basis.java` — enum
```java
public enum Basis { RECTILINEAR, DIAGONAL }   // '+' and 'x'
```
(For E91, a richer angle-based basis is modeled inline as `double theta`; see §7.)

### 3.3 `crypto/QubitDescriptor.java` — channel carrier (immutable value object)
Represents one prepared qubit *as transmitted*. Carries the classical description the
receiver needs to re-prepare and measure, plus a slot the channel/Eve can mutate by
returning a new descriptor (records are immutable; channel returns replacements).
```java
public record QubitDescriptor(int bit, Basis basis) {}
```
The receiver never sees `bit`/`basis` directly in protocol logic — only the channel and
the preparing party do. (Encapsulation: the receiver gets a descriptor and is only
allowed to run a measurement program against it; see `QuantumChannel.measure`.)

### 3.4 `crypto/QuantumChannel.java` — simulated one-qubit channel
Central abstraction that ties parties to the simulator and is the single insertion
point for an eavesdropper.
```java
public final class QuantumChannel {
    private final QuantumExecutionEnvironment qee;
    private Eavesdropper eve;                 // null => clean channel
    public QuantumChannel(QuantumExecutionEnvironment qee) { ... }
    public void attachEavesdropper(Eavesdropper e) { this.eve = e; }

    /** Build a 1-qubit prepare program for (bit,basis). */
    static Program prepareProgram(QubitDescriptor d);

    /** Re-prepare descriptor, append measurement-basis rotation, run, return 0/1. */
    public int measure(QubitDescriptor sent, Basis measureBasis) {
        QubitDescriptor delivered = (eve == null) ? sent : eve.intercept(this, sent);
        Program p = prepareProgram(delivered);          // re-prepare state
        if (measureBasis == Basis.DIAGONAL) p.addStep(new Step(new Hadamard(0)));
        int outcome = qee.runProgram(p).getQubits()[0].measure();
        return outcome;
    }
}
```
Key point: because the simulator is probabilistic, when `delivered.basis !=
measureBasis` the outcome is correctly random 50/50, which is exactly what produces the
expected QBER statistics. `measure` is the only path qubits flow through — clean vs.
eavesdropped differ only by the `eve` hook.

---

## 4. Work item: QRNG + NIST STS test harness

### 4.1 Files
- `crypto/QuantumRandom.java` (see §3.1).
- `crypto/nist/StatisticalTest.java` — interface.
- `crypto/nist/TestResult.java` — record `(String name, double pValue, boolean passed)`.
- `crypto/nist/NistTestSuite.java` — runs a configured list of tests over a `boolean[]`
  / `int[]` bit array (or `byte[]`), default α = 0.01.
- Individual tests under `crypto/nist/`:
  - `FrequencyMonobitTest.java`
  - `BlockFrequencyTest.java`
  - `RunsTest.java`
  - `LongestRunOfOnesTest.java`
  - `CumulativeSumsTest.java`

### 4.2 Interface
```java
public interface StatisticalTest {
    String name();
    TestResult run(int[] bits);      // bits are 0/1
}
```
`NistTestSuite.runAll(int[] bits)` → `List<TestResult>`; `allPassed(α)` helper.

### 4.3 Representative subset — algorithms (concrete)
Implement the standard NIST SP 800-22 formulas. p-values use the complementary error
function `erfc` and the regularized upper incomplete gamma `igamc`; provide a tiny
`crypto/nist/SpecialMath.java` with `erfc(double)` (Abramowitz-Stegun 7.1.26) and
`igamc(double a, double x)` (continued-fraction / series) — no external deps.

1. **Frequency (Monobit).** `S = Σ(2*bit−1)`; `sObs = |S|/√n`;
   `p = erfc(sObs/√2)`. Recommended `n ≥ 100`.
2. **Block Frequency.** Partition into `N` blocks of size `M`; per-block `π_i = (#ones)/M`;
   `χ² = 4M Σ(π_i−0.5)²`; `p = igamc(N/2, χ²/2)`. Require `M ≥ 20`, `M > 0.01n`.
3. **Runs.** Precondition on monobit fraction `π` (|π−0.5| < 2/√n else fail/skip);
   `Vn = 1 + Σ_{k} [bit_k != bit_{k+1}]`;
   `p = erfc(|Vn − 2nπ(1−π)| / (2√(2n) π(1−π)))`.
4. **Longest Run of Ones in a Block.** Use the standard `M=8`/`K=3` parameterization
   for small n (`n ≥ 128`): tabulate longest-run-of-ones per block, bucket, compute
   `χ²` against fixed probabilities `{0.2148,0.3672,0.2305,0.1875}`,
   `p = igamc(K/2, χ²/2)`.
5. **Cumulative Sums (forward).** Random walk `Sk` of `±1`; `z = max|Sk|`; p-value via the
   standard sum over normal CDF terms (`Φ`). Provide `Φ` from `erfc`.

Each returns `passed = pValue ≥ α` (α default 0.01).

### 4.4 QRNG sequencing
1. `Basis`, `QubitDescriptor`, `QuantumChannel`, `QuantumRandom` skeletons.
2. `SpecialMath` (erfc, igamc, Φ) + unit tests against known values.
3. The five tests, each unit-tested with hand-crafted bit arrays whose p-values are
   computable by hand or against the NIST example vectors in SP 800-22 (the spec lists
   worked examples, e.g. the ε of length 100 / 128 — encode those as fixtures).
4. `NistTestSuite` wiring + a demo `crypto/demo/QrngDemo.java` that draws e.g. 100k bits
   and prints the table.

---

## 5. Work item: BB84

### 5.1 Files
- `crypto/Party.java` — small base holding a `QuantumRandom` and helpers
  (`randomBit()`, `randomBasis()`).
- `crypto/Alice.java`, `crypto/Bob.java` — roles.
- `crypto/bb84/BB84Protocol.java` — orchestration + result type.
- `crypto/bb84/BB84Result.java` — record with sifted key (both sides), QBER, sample
  size, raw lengths.

### 5.2 Roles
```java
public class Alice extends Party {
    /** Pick a random bit and basis, return descriptor to send. Records its choices. */
    public QubitDescriptor prepare();            // appends to internal bits/bases lists
    public List<Integer> bits();  public List<Basis> bases();
}
public class Bob extends Party {
    public Basis chooseBasis();                  // random
    /** measure delivered descriptor via channel in a chosen basis; record result+basis */
    public int measure(QuantumChannel ch, QubitDescriptor sent, Basis basis);
    public List<Integer> results(); public List<Basis> bases();
}
```

### 5.3 Protocol steps (`BB84Protocol.run(int nRaw, double sampleFraction)`)
1. **Preparation/transmission.** For `i in [0,nRaw)`: `QubitDescriptor d = alice.prepare()`
   (random bit `a_i`, random basis `Ba_i`). Alice "sends" `d` over the `QuantumChannel`.
2. **Measurement.** Bob picks `Bb_i = bob.chooseBasis()` and
   `b_i = ch.measure(d, Bb_i)`. (If Eve is attached to the channel, interception happens
   transparently inside `measure`.)
3. **Basis sifting.** Over the public classical channel (just compare the recorded basis
   lists), keep indices where `Ba_i == Bb_i`. Discard the rest. Sifted key length ≈
   `nRaw/2`.
4. **QBER estimation.** Randomly choose a `sampleFraction` subset of the sifted indices,
   publicly reveal Alice's and Bob's bits there, count mismatches →
   `qber = mismatches / sampleSize`. Remove revealed bits from the key.
5. **Eavesdropper detection.** If `qber > threshold` (default ~0.11, i.e. comfortably
   below the 0.25 intercept-resend signature and above clean-channel noise ≈0), declare
   the channel compromised and abort; else accept remaining sifted bits as the shared
   secret key.
6. Return `BB84Result(aliceKey, bobKey, qber, sampleSize, siftedLength)`. Add an optional
   final equality check `aliceKey.equals(bobKey)` for the clean case (should be true).

### 5.4 Why it works (for tests & docstrings)
- **No Eve:** when `Ba_i == Bb_i`, Bob's re-prepared state is measured in the matching
  basis → deterministic agreement → QBER ≈ 0.
- **With Eve (intercept-resend):** see §6. On sifted (matching-basis) positions Eve
  guesses the wrong basis half the time and, when wrong, randomizes Bob's outcome →
  expected QBER = 0.5 × 0.5 = **0.25**.

---

## 6. Work item: Eavesdropper (intercept-resend) + detection

### 6.1 Files
- `crypto/Eavesdropper.java` — interface.
- `crypto/eve/InterceptResendEve.java` — implementation.

### 6.2 Interface & implementation
```java
public interface Eavesdropper {
    /** Receive the in-flight descriptor, possibly measure & resend; return what Bob gets. */
    QubitDescriptor intercept(QuantumChannel ch, QubitDescriptor sent);
}

public final class InterceptResendEve implements Eavesdropper {
    private final QuantumRandom rng;
    @Override public QubitDescriptor intercept(QuantumChannel ch, QubitDescriptor sent) {
        Basis eveBasis = rng.nextBit() == 0 ? Basis.RECTILINEAR : Basis.DIAGONAL;
        int eveBit = ch.measureRaw(sent, eveBasis);     // helper that runs prepare+rotate+measure
        return new QubitDescriptor(eveBit, eveBasis);   // resend in the basis Eve measured
    }
}
```
- Add `QuantumChannel.measureRaw(QubitDescriptor, Basis)` = the same prepare→rotate→
  measure run used in `measure`, but bypassing the `eve` hook (so Eve does not recurse).
- Eve records her own basis/bit lists for analysis/demo of partial information gained.

### 6.3 Detection (statistical)
- Detection is purely the QBER step in BB84 (§5.3 step 4–5). Expected sifted-key QBER:
  - clean channel: ≈ 0 (exactly 0 with the deterministic simulator on matching bases).
  - full intercept-resend: ≈ **0.25**.
- The test asserts `qber` is near 0 without Eve and near 0.25 (within statistical
  tolerance for the chosen `nRaw`) with Eve, and that the protocol aborts when
  `qber > threshold`.
- Optional generalization: a `probability p` of interception per qubit gives
  `QBER ≈ 0.25 p`, useful for a sensitivity demo.

---

## 7. Work item: B92

### 7.1 Files
- `crypto/b92/B92Protocol.java`, `crypto/b92/B92Result.java`.

### 7.2 Protocol (two non-orthogonal states)
Alice encodes one classical bit per qubit using just two states:
- bit `0` → `|0>` (prepare: identity).
- bit `1` → `|+>` (prepare: `Hadamard(0)`).

Bob randomly measures each qubit in either the **rectilinear** (`+`, i.e. straight
measure) or **diagonal** (`×`, i.e. `Hadamard(0)` then measure) basis and applies the
B92 **conclusive-result** rule:
- If Bob measures in `+` and gets `1` → state was not `|0>` ⇒ Alice's bit was `1`.
- If Bob measures in `×` and gets `1` → state was not `|+>` ⇒ Alice's bit was `0`.
- A `0` outcome is **inconclusive** and discarded.

Steps:
1. For each `i`: `aBit = alice.randomBit()`; build descriptor encoding the chosen
   non-orthogonal state (reuse `QubitDescriptor` with a `B92State` enum, or special-case
   in a `prepareProgramB92`).
2. Bob: `mBasis = random`; `out = ch.measureB92(desc, mBasis)`.
3. Keep only conclusive (`out == 1`) positions; Bob infers `aBit` per the rule above.
4. Sifting/QBER/abort identical in spirit to BB84 (publicly sample a subset, compute
   error, abort if too high). Roughly 25 % of qubits yield a conclusive bit on a clean
   channel.

Reuse the same `QuantumChannel` + `Eavesdropper` machinery; intercept-resend again
introduces detectable errors among conclusive results.

---

## 8. Work item: E91 (entangled pairs) + CHSH test

### 8.1 Files
- `crypto/e91/E91Protocol.java`, `crypto/e91/E91Result.java`, `crypto/e91/ChshTest.java`.

### 8.2 Entangled-pair model (2-qubit programs)
Unlike BB84/B92, E91 needs a genuine 2-qubit entangled state, so each round is a
2-qubit `Program`. A source produces the singlet/Bell pair; Alice measures qubit 0,
Bob measures qubit 1, each along an independently chosen angle.

Bell-pair preparation (Φ+ = (|00>+|11>)/√2) on qubits {0,1}:
```java
Program p = new Program(2,
    new Step(new Hadamard(0)),
    new Step(new Cnot(0, 1)));
```
(Use Φ+ and account for correlation signs in the analysis, or apply an `X`/`Z` to make
the anti-correlated singlet — pick one and keep the CHSH sign bookkeeping consistent.)

Per-party measurement angle is applied with `RotationY(theta, idx)` *before* measuring,
which rotates the chosen measurement axis into the computational basis:
```java
if (aliceAngle != 0) p.addStep(new Step(new RotationY(-2*aliceAngle, 0)));
if (bobAngle   != 0) p.addStep(new Step(new RotationY(-2*bobAngle,   1)));
Result r = qee.runProgram(p);
int a = r.getQubits()[0].measure();
int b = r.getQubits()[1].measure();
```
(The `-2θ` factor accounts for `RotationY`'s half-angle convention; calibrate against a
known correlation `E(θa,θb)=cos(2(θa−θb))` or `−cos…` per chosen Bell state in a unit
test before relying on it.)

### 8.3 Protocol
- Alice picks among angles `Aa ∈ {0°, 45°, 90°}`; Bob among `Bb ∈ {45°, 90°, 135°}`.
- **Key generation:** rounds where Alice and Bob used *aligned* axes (e.g. Alice 45°
  vs Bob 45°, Alice 90° vs Bob 90°) yield perfectly (anti)correlated bits → shared key
  (one side flips bits if using the anti-correlated singlet).
- **Security via CHSH:** rounds where axes differ feed the CHSH correlator
  `S = E(a1,b1) − E(a1,b3) + E(a3,b1) + E(a3,b3)`, where
  `E(x,y) = (N00 + N11 − N01 − N10)/Ntot` per angle pair.
  - Ideal entanglement: `|S| ≈ 2√2 ≈ 2.828` (Tsirelson bound).
  - Local-hidden-variable / eavesdropped (broken entanglement): `|S| ≤ 2`.
- **Detection:** if measured `|S|` drops toward/below 2, an eavesdropper (or noise) has
  degraded the entanglement → abort. `ChshTest.compute(...)` returns `S` and a boolean
  `violatesBell = |S| > 2 + margin`.

### 8.4 Eve for E91
Model intercept-resend on the entangled channel as Eve measuring one travelling qubit
in a random basis and resending a product (separable) state. Implement this by, in the
Eve-on path, collapsing the corresponding qubit's preparation to a fixed basis state
before Bob's rotation — the simplest faithful effect is to run a 1-qubit measurement of
Eve's qubit and feed a separable re-prepared qubit into Bob's program, which mechanically
drives `S` toward ≤ 2.

---

## 9. Work item: Quantum secret sharing (design-level)

### 9.1 Files
- `crypto/secretsharing/QuantumSecretSharing.java`, `...SharingResult.java`.

### 9.2 Design (GHZ-based (n,n) threshold sketch)
- Dealer prepares an `n`-qubit GHZ state `(|0…0> + |1…1>)/√2`:
  ```java
  Program p = new Program(n,
      new Step(new Hadamard(0)),
      new Step(new Cnot(0,1)), new Step(new Cnot(1,2)), ...);  // chain CNOTs
  ```
  (Strange CNOTs are pairwise; chain `Cnot(i,i+1)` across steps to spread the GHZ
  correlation — verify the resulting state in a unit test.)
- Each of `n` players receives one qubit. Each randomly measures in `X` (apply
  `Hadamard` then measure) or `Y` (apply `RotationX`/phase then `Hadamard`) basis.
- Players announce **bases** (not outcomes). For the GHZ correlation, certain
  basis combinations make the **parity** of all outcomes deterministic; the dealer's
  secret bit is recovered only by XORing all `n` outcomes — no proper subset learns
  anything (information-theoretic `(n,n)` sharing).
- `share(int secretBit)` → per-player measurement records; `reconstruct(records)` →
  recovered bit, succeeding only if all `n` shares are present and bases are compatible.
- Note `(k,n)` threshold sharing requires a richer (e.g. CSS/qudit) construction —
  document as future work; ship the `(n,n)` GHZ version concretely.

---

## 10. Work item: Quantum digital signatures (design-level)

### 10.1 Files
- `crypto/signatures/QuantumDigitalSignature.java`, `...KeyDistribution.java`,
  `...Signature.java`.

### 10.2 Design (Gottesman-Chuang style, information-theoretic, simplified)
Three phases for a 1-bit message (extend by repetition):
1. **Key distribution / distribution phase.** For each possible message bit
   `m ∈ {0,1}`, signer generates a long sequence of BB84-style non-orthogonal states
   (`{|0>,|1>,|+>,|−>}`) — the *private key* is the classical sequence (bits+bases), the
   *public key* is the corresponding quantum-state sequence. Copies are distributed to
   recipients via the existing `QuantumChannel` + symmetrization (recipients secretly
   swap subsets so no recipient is privileged) — model symmetrization as a permutation
   over recorded descriptor lists.
2. **Signing.** To sign bit `m`, signer reveals the classical description (bit+basis) of
   the sequence associated with `m`.
3. **Verification.** Each recipient measures their held quantum states in the revealed
   bases and counts **mismatches** against the revealed classical values:
   - mismatches below an **acceptance threshold** `s_a` → accept.
   - between `s_a` and a **rejection threshold** `s_v` → accept-but-flag (transferability
     region).
   - above `s_v` → reject (forgery/repudiation detected).
   Security is information-theoretic: a forger cannot guess the non-orthogonal states
   well enough to stay under the threshold for all recipients (non-repudiation +
   unforgeability + transferability follow from the gap `s_a < s_v`).

API sketch:
```java
public final class QuantumDigitalSignature {
    public KeyDistribution distribute(int seqLength, int recipients);
    public Signature sign(int message, KeyDistribution kd);
    public enum Verdict { ACCEPT, FLAGGED, REJECT }
    public Verdict verify(int recipient, Signature sig, KeyDistribution kd, QuantumChannel ch);
}
```
Reuse `QubitDescriptor`, `QuantumChannel`, and the same prepare/measure machinery; the
mismatch-counting verification mirrors BB84 QBER estimation.

---

## 11. Testing strategy

Add JUnit tests under the repo's existing test layout (mirror how current algorithm
tests run). Use a fixed, modest `nRaw` and statistical tolerances.

1. **QRNG smoke:** `nextBits(1000)` returns only 0/1; mean ≈ 0.5 ± tolerance;
   `nextBytes` length and packing correct.
2. **NIST harness:**
   - Unit-test `SpecialMath.erfc/igamc/Φ` against known reference values.
   - Each test against SP 800-22 worked-example vectors (fixed bit strings with known
     p-values) → exact-ish p-value match.
   - Integration: feed `QuantumRandom` output (≥ 100k bits) through `NistTestSuite`;
     assert **all selected tests pass at α = 0.01** (allow occasional re-draw to avoid
     flaky 1 %-tail failures: run with a couple of retries or a larger n).
3. **BB84 no Eve:** `BB84Protocol.run(nRaw=2000)` → `qber == 0.0` (deterministic
   simulator on matching bases) and `aliceKey.equals(bobKey)`; sifted length ≈ nRaw/2
   within tolerance; protocol does NOT abort.
4. **BB84 with Eve:** attach `InterceptResendEve` → `qber ≈ 0.25` (assert within e.g.
   ±0.05 for nRaw=2000) and protocol **aborts** (qber > threshold).
5. **B92:** clean channel → low error on conclusive bits and Alice/Bob agree; conclusive
   fraction ≈ 0.25; with Eve → elevated error.
6. **E91 / CHSH:** clean entanglement → `|S| ≈ 2.83` (assert `> 2.4`); aligned-axis
   rounds give matching keys; with Eve / separable resend → `|S| ≤ 2` (assert `< 2.1`)
   and abort. First calibrate the `RotationY` angle convention in a dedicated test
   (`E(0,0)`, `E(0,45°)`, `E(0,90°)`) before asserting CHSH.
7. **Secret sharing:** GHZ `(n,n)` reconstruct returns the dealer's secret iff all shares
   present; any missing/proper subset → cannot reconstruct.
8. **Digital signatures:** honest sign→verify → `ACCEPT`; tampered classical reveal or
   wrong message → `REJECT`.

Determinism note: because `Qubit.measure()` reads simulator-collapsed values, where a
test needs reproducibility inject a controllable QEE via the `setQuantumExecutionEnvironment`
hooks (mirror `Classic.setQuantumExecutionEnvironment`), or assert only on aggregate
statistics with generous tolerances and retries.

---

## 12. Risks & mitigations

- **Live-qubit handoff is not supported by Strange.** Mitigation: the descriptor +
  re-prepare model (§3) is the correct fidelity-preserving approach; document it clearly
  so reviewers don't expect persistent shared qubit state.
- **`RotationY` half-angle / sign convention.** E91 correctness hinges on it. Mitigation:
  a dedicated calibration test (§11.6) before any CHSH assertion; pick the Bell state and
  rotation sign empirically to match `E(θa,θb)=±cos(2(θa−θb))`.
- **Statistical flakiness** (NIST 1 %-tail, QBER variance). Mitigation: large n, generous
  tolerances, bounded retries; never assert exact equality on random quantities (except
  the deterministic clean-BB84 QBER==0 case).
- **Simulator cost** of wide batched QRNG (`2^n`). Mitigation: cap batch width (≤16),
  default to the 1-qubit loop.
- **`Program.ensureMeasuresafe`** forbids superposition gates after a measurement on the
  same qubit. Mitigation: always order steps prepare → rotate → measure.
- **`igamc`/`erfc` numerical accuracy** affects p-values. Mitigation: use the standard
  series+continued-fraction `igamc` and AS 7.1.26 `erfc`; cover with reference-value unit
  tests.
- **Scope creep on `(k,n)` sharing and full QDS.** Mitigation: ship concrete `(n,n)` GHZ
  sharing and simplified QDS; mark advanced variants as future work.

---

## 13. Suggested sequencing (ordered)

1. **Infra:** `Basis`, `QubitDescriptor`, `QuantumChannel`, `QuantumRandom` (+ batched
   fast path). Unit-test QRNG basics.
2. **NIST harness:** `SpecialMath`, the five tests, `NistTestSuite`, `QrngDemo`. Tests
   against SP 800-22 vectors + integration over QRNG output.
3. **BB84 core:** `Party`, `Alice`, `Bob`, `BB84Protocol`, `BB84Result`. Clean-channel
   tests (QBER 0, keys equal).
4. **Eavesdropper:** `Eavesdropper`, `InterceptResendEve`, `measureRaw` hook. Tests for
   QBER ≈ 0.25 and abort.
5. **B92:** protocol + result, reusing channel/Eve. Tests.
6. **E91:** Bell-pair prep, `RotationY` calibration, `ChshTest`, `E91Protocol`, Eve
   variant. Tests for `|S| ≈ 2.83` vs `≤ 2`.
7. **Secret sharing:** GHZ `(n,n)`. Tests.
8. **Digital signatures:** distribution/sign/verify. Tests.
9. **Demos** under `crypto/demo/` for each primitive; wire into any existing demo runner
   if present.

Each step is independently compilable and testable; steps 5–8 reuse the infra and Eve
from steps 1–4, so they can be parallelized once the channel API is frozen after step 4.
