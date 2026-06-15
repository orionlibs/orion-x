# Section 14 — Build & Ecosystem: Implementation Plan

## Overview & Goals

This section makes Strange a first-class, reproducibly-built, multi-platform
library rather than a loose tree of `.java` files. Concretely:

1. **A real build file** — discover what the build is (it currently isn't one)
   and stand up a Maven build that matches the upstream Strange conventions.
2. **Maven BOM** — a `packaging=pom` artifact that pins compatible versions of
   `strange`, `strangefx`, and `strange-eom` so downstream apps depend on one
   coordinate and get an aligned set.
3. **JPMS `module-info.java`** — turn the codebase into a named module
   `org.redfx.strange` with explicit `exports`, designed to *grow* as later
   sections add packages (`.crypto`, `.noise`, `.transpiler`, `.qasm`, …).
4. **GraalVM native-image profile** — reflection/resource config plus an AOT
   profile so CLI tools (the demos, a future QASM transpiler CLI) ship as
   self-contained binaries.
5. **OSGi bundle manifest** — `Export-Package`/`Import-Package` headers via
   `bnd-maven-plugin` so the jar drops into Eclipse RCP / NetBeans Platform.
6. **GitHub Actions CI** — test matrix JDK 17 / 21 / 25-ea, a native-image job,
   and a tag-triggered Maven Central (Central Portal) release with GPG signing.

The recurring theme is that **JPMS, OSGi, and native-image each impose their own
metadata and each dislikes the other two** (see Risks). The plan keeps a single
source of truth and generates the rest.

---

## Current Build State (exactly what was found)

Inspected the repo at
`/Users/dimiefthymiou/workspaces/misc/orion-x/quantum-compuing/strage-source`.

- **No build file at all.** There is no `pom.xml`, no `build.gradle(.kts)`, no
  `settings.gradle`, no `Makefile`. `find` for build files returned nothing.
- **No `.github/` directory** — no CI of any kind.
- **No `module-info.java`** and no `META-INF/MANIFEST.MF` / `bnd.bnd`.
- **Flat, non-standard source layout.** Sources sit at the repo root and in five
  package dirs, *not* under `src/main/java`. `find -type d` shows only:
  `algorithm/`, `cloud/`, `demo/`, `gate/`, `local/`, `plans/`.
- **No test directory and no tests.** Nothing under `src/test`, no JUnit on the
  classpath. (Section 10 adds `QuantumAssert` etc.; this plan provisions the
  test wiring so those land cleanly.)
- **Packages present** (from `package` declarations across all `.java` files):
  - `org.redfx.strange` — 11 files at root: `Block`, `BlockGate`, `Complex`,
    `ControlledBlockGate`, `Gate`, `Program`, `QuantumExecutionEnvironment`,
    `Qubit`, `Qubits`, `Result`, `Step`.
  - `org.redfx.strange.gate` — 33 gate classes (`Hadamard`, `Cnot`, `Fourier`,
    `Oracle`, `Toffoli`, `RotationX/Y/Z`, the `*Modulus` arithmetic gates, the
    `SingleQubitGate`/`TwoQubitGate`/`ThreeQubitGate` base classes, …).
  - `org.redfx.strange.local` — `SimpleQuantumExecutionEnvironment`,
    `Computations`.
  - `org.redfx.strange.cloud` — `CloudlinkQuantumExecutionEnvironment`,
    `ResultConverter` (both currently **stubs**: their Gluon CloudLink /
    `javax.json` imports are commented out).
  - `org.redfx.strange.algorithm` — `Classic` (uses a static
    `QuantumExecutionEnvironment qee` field).
  - `org.redfx.strange.demo` — `Demo` (the **only** class with a
    `public static void main` → the native-image / CLI entry point).
- **Zero external runtime dependencies.** A grep for non-JDK imports across all
  sources returns nothing — every import is `java.*`/`javax.*` (and the only
  `javax.json` usage in `cloud/` is commented out). The library is pure JDK
  today.
- **License header = `license-maven-plugin` style.** Every file opens with the
  Mojohaus license-maven-plugin markers:
  ```
  /*-
   * #%L
   * Strange
   * %%
   * Copyright (C) 2020 Johan Vos
  ```
  The `#%L` / `%%` / `#L%` delimiters are exactly what
  `org.codehaus.mojo:license-maven-plugin` emits. **This is strong evidence the
  canonical upstream build is Maven**, so this plan standardises on Maven (and
  notes Gradle as an alternative in Risks).
- **Reflection touch-points found** (relevant to native-image):
  - `SingleQubitGate`, `TwoQubitGate`, `ThreeQubitGate` all implement
    `getName()` as `return this.getClass().getName();` — gate identity is the
    FQCN, which native-image will mangle/strip without config.
  - `Computations` logs `myGate.getClass()`.
  - `Classic` resolves the environment via a mutable static field (no
    `ServiceLoader`/`Class.forName` today) — see the recommendation to make
    backend discovery `ServiceLoader`-based, which interacts with both JPMS
    `provides`/`uses` and native-image service config.
  - The (commented) cloud path does `javax.json` (de)serialisation of
    `Program`/`Step`/`Gate`; Section 10's JSON round-trip will reintroduce
    reflective field access → must be registered for native-image.
- **Coordinates (inferred, to confirm against upstream `gluonhq/strange`):**
  groupId `org.redfx`, artifactId `strange`, sibling `strangefx`. Version is not
  declarable from the repo (no POM); the plan parameterises it.

> Net: this is a **greenfield build setup**. Nothing has to be migrated, but the
> non-standard flat layout must be reconciled with Maven's expectations.

---

## Detailed Work Items

Ordered. Each lists target file paths and concrete snippets.

### WI-0 — Decide source layout (prerequisite for everything)

Maven, JPMS, OSGi (bnd), and native-image plugins all assume the standard
`src/main/java` layout. Two options:

- **Option A (recommended): move sources into `src/main/java/…`.** One-time
  `git mv` per package dir. Cleanest; all tooling works with defaults.
  ```
  src/main/java/org/redfx/strange/*.java          (the 11 root files)
  src/main/java/org/redfx/strange/gate/*.java
  src/main/java/org/redfx/strange/local/*.java
  src/main/java/org/redfx/strange/cloud/*.java
  src/main/java/org/redfx/strange/algorithm/*.java
  src/main/java/org/redfx/strange/demo/*.java
  src/main/java/module-info.java
  src/test/java/org/redfx/strange/…                (new, for §10 tests)
  ```
- **Option B: keep flat layout, point Maven at it** via
  `<sourceDirectory>.</sourceDirectory>`. Avoids the move but breaks bnd-plugin
  and native-image conventions and pollutes the jar root with `plans/`. **Not
  recommended.**

This plan assumes **Option A** in all subsequent paths. (This is the only WI
that touches existing files — and only by *moving*, not editing them. Per the
task constraint, no source file content is modified.)

### WI-1 — Root `pom.xml` (the `strange` artifact)

Create `/pom.xml`. Pure-JDK library, JDK 17 baseline (the matrix also tests 21
and 25-ea), JPMS-aware.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.redfx</groupId>
  <artifactId>strange</artifactId>
  <version>0.1.5-SNAPSHOT</version>   <!-- align with upstream gluonhq/strange -->
  <packaging>jar</packaging>

  <name>Strange</name>
  <description>Quantum computing library for Java</description>
  <url>https://github.com/gluonhq/strange</url>

  <licenses>
    <license>
      <name>BSD-3-Clause</name>
      <url>https://opensource.org/licenses/BSD-3-Clause</url>
    </license>
  </licenses>
  <developers>
    <developer><name>Johan Vos</name><organization>Gluon</organization></developer>
  </developers>
  <scm>
    <connection>scm:git:https://github.com/gluonhq/strange.git</connection>
    <developerConnection>scm:git:git@github.com:gluonhq/strange.git</developerConnection>
    <url>https://github.com/gluonhq/strange</url>
    <tag>HEAD</tag>
  </scm>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>17</maven.compiler.release>
    <junit.version>5.11.3</junit.version>
  </properties>

  <dependencies>
    <!-- No runtime deps today (pure JDK). Test-only: -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <!-- release=17 + module-info.java compiles on the module path -->
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
      </plugin>
      <!-- keep the existing license headers verifiable -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>license-maven-plugin</artifactId>
        <version>2.4.0</version>
        <configuration>
          <licenseName>bsd_3</licenseName>
          <organizationName>Johan Vos</organizationName>
          <inceptionYear>2020</inceptionYear>
        </configuration>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <!-- WI-4: GraalVM native-image; WI-7: release. Defined below. -->
  </profiles>
</project>
```

Notes:
- `maven.compiler.release=17` is the floor; the CI matrix exercises 21/25-ea by
  running the same build under those JDKs (no per-JDK release bump needed).
- Surefire on the module path "just works" once `module-info.java` exists; if
  tests need deep reflection into the module, add
  `--add-opens org.redfx.strange/...=ALL-UNNAMED` in `argLine` (or keep tests in
  the *same* module via the standard "patch-module" surefire behaviour).

### WI-2 — `module-info.java` (JPMS)

Create `/src/main/java/module-info.java`. Export the public packages; the
`local`/`cloud` impls are exported because `Classic` lets callers inject any
`QuantumExecutionEnvironment` and the simulator is part of the public surface
today. Recommend introducing a `ServiceLoader` SPI for backends so future
hardware backends (Section 6) plug in without re-exporting internals.

```java
/*-
 * #%L  Strange  %%  Copyright (C) 2020 Johan Vos  ...  #L%   (BSD header retained)
 */
module org.redfx.strange {

    // --- exported API (current packages) ---
    exports org.redfx.strange;            // Program, QuantumStep, Gate, Complex, Result, Qubit(s)
    exports org.redfx.strange.gate;       // all standard gates
    exports org.redfx.strange.local;      // SimpleQuantumExecutionEnvironment
    exports org.redfx.strange.cloud;      // Cloudlink* (stub today)
    exports org.redfx.strange.algorithm;  // Classic + future Deutsch-Jozsa, VQE, ...

    // demo/ is intentionally NOT exported (it is the CLI entry point, not API).
    // It is reachable as a main class but kept encapsulated.

    // --- backend SPI (recommended; pairs with making Classic non-static) ---
    uses org.redfx.strange.QuantumExecutionEnvironment;
    provides org.redfx.strange.QuantumExecutionEnvironment
        with org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

    // --- requires (pure JDK today) ---
    requires java.logging;                // java.util.logging.Logger in local/, gate/
    // requires java.net.http;            // ADD when cloud/ HTTP backend is un-stubbed (Section 6)
    // requires jakarta.json;             // ADD for JSON round-trip (Section 10) — keep optional

    /*
     * EVOLUTION (future sections add packages — append exports here):
     *   exports org.redfx.strange.crypto;       // §9 QKD/QRNG
     *   exports org.redfx.strange.noise;        // §5 noise models
     *   exports org.redfx.strange.transpiler;   // §4 decompose/route/schedule
     *   exports org.redfx.strange.qasm;         // §6 OpenQASM import/export
     *   exports org.redfx.strange.info;         // §8 tomography / entropy
     *   provides ... with org.redfx.strange.cloud.IbmQuantumEnvironment; // §6
     */
}
```

Decisions / cautions:
- **No split packages allowed.** If StrangeFX or StrangeEOM also declare
  `org.redfx.strange.*` packages, JPMS will reject both on the module path
  (a package may belong to exactly one module). Audit StrangeFX before release
  (see Risks). The BOM (WI-3) is the place to enforce compatible, non-overlapping
  versions.
- Making backend lookup a `ServiceLoader` (`uses`/`provides`) is optional but
  strongly recommended now, because retrofitting it later forces a module
  descriptor change *and* native-image service config simultaneously.
- The `cloud` package currently has no live deps; only add `requires
  java.net.http` / `jakarta.json` when Section 6/10 un-stub it, and prefer
  `requires static` for anything optional so the core stays dependency-free.

### WI-3 — Maven BOM (`strange-bom`)

New sibling module `bom/pom.xml` with `packaging=pom`. It carries **no code**;
its job is `dependencyManagement` aligning the three projects so a consumer
writes one `import`-scoped dependency and never specifies versions.

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.redfx</groupId>
  <artifactId>strange-bom</artifactId>
  <version>0.1.5-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Strange BOM</name>

  <properties>
    <strange.version>0.1.5</strange.version>
    <strangefx.version>0.1.5</strangefx.version>     <!-- StrangeFX release line -->
    <strangeeom.version>0.1.5</strangeeom.version>    <!-- StrangeEOM release line -->
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.redfx</groupId><artifactId>strange</artifactId>
        <version>${strange.version}</version>
      </dependency>
      <dependency>
        <groupId>org.redfx</groupId><artifactId>strangefx</artifactId>
        <version>${strangefx.version}</version>
      </dependency>
      <dependency>
        <groupId>org.redfx</groupId><artifactId>strangeeom</artifactId>
        <version>${strangeeom.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

Consumer usage:
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.redfx</groupId><artifactId>strange-bom</artifactId>
      <version>0.1.5</version><type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<!-- then depend on strange / strangefx with no <version> -->
```

The BOM is released as its own artifact on the same tag (WI-6 publishes it
alongside `strange`). Verify the **groupId of StrangeFX/StrangeEOM** against
their real POMs before pinning — adjust if they are not `org.redfx`.

### WI-4 — GraalVM native-image config + profile

Two pieces: (a) reflection/resource JSON config under `META-INF`, (b) a Maven
profile that runs the native-image build.

**(a) Config files** at
`/src/main/resources/META-INF/native-image/org.redfx/strange/`:

`reflect-config.json` — register the gate hierarchy (because `getName()` returns
the FQCN and any future `ServiceLoader`/JSON deserialiser instantiates gates by
class name) and the backend impls:
```json
[
  { "name": "org.redfx.strange.local.SimpleQuantumExecutionEnvironment",
    "allDeclaredConstructors": true, "allPublicMethods": true },
  { "name": "SingleQubitGate", "allDeclaredConstructors": true },
  { "name": "TwoQubitGate",    "allDeclaredConstructors": true },
  { "name": "ThreeQubitGate",  "allDeclaredConstructors": true },
  { "name": "Hadamard", "allDeclaredConstructors": true },
  { "name": "Cnot",     "allDeclaredConstructors": true }
  /* ... enumerate all concrete gates, or generate via the tracing agent (below) ... */
]
```

`resource-config.json` — include the native-image config itself and (later) any
bundled QASM/JSON templates. `serialization-config.json` — needed once Section 10
adds Java/JSON (de)serialisation of `Program`/`Step`/`Gate`.

**Generate, don't hand-maintain:** run the tracing agent over the demo / test
suite so the lists stay correct as gates are added:
```bash
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/org.redfx/strange \
     -cp target/classes org.redfx.strange.demo.Demo
```

**(b) Profile** in root `pom.xml`:
```xml
<profile>
  <id>native</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
        <version>0.10.3</version>
        <extensions>true</extensions>
        <executions>
          <execution><id>build-native</id>
            <goals><goal>compile-no-fork</goal></goals>
            <phase>package</phase></execution>
        </executions>
        <configuration>
          <imageName>strange-cli</imageName>
          <mainClass>org.redfx.strange.demo.Demo</mainClass>
          <buildArgs>
            <buildArg>--no-fallback</buildArg>
            <buildArg>-O3</buildArg>
          </buildArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```
`Demo` is the confirmed CLI entry point (only class with `main`). As CLI tools
grow (a QASM transpiler, a benchmark runner), add additional `imageName`/
`mainClass` executions or split a thin `strange-cli` module.

### WI-5 — OSGi bundle manifest (bnd)

Use `bnd-maven-plugin` so OSGi headers are derived from the module and don't
drift. Add to root `pom.xml` (active by default — the headers are harmless on a
plain classpath):
```xml
<plugin>
  <groupId>biz.aQute.bnd</groupId>
  <artifactId>bnd-maven-plugin</artifactId>
  <version>7.0.0</version>
  <executions>
    <execution><goals><goal>bnd-process</goal></goals></execution>
  </executions>
</plugin>
<!-- and tell the jar plugin to use the bnd-generated manifest -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-jar-plugin</artifactId>
  <version>3.4.1</version>
  <configuration>
    <archive><manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile></archive>
  </configuration>
</plugin>
```

`/bnd.bnd` (single source of OSGi truth):
```
Bundle-SymbolicName: org.redfx.strange
Bundle-Name:         Strange
Bundle-Version:      ${project.version}
Bundle-License:      BSD-3-Clause

# Export the same packages JPMS exports (mirror WI-2). Keep versions in sync.
Export-Package: \
  org.redfx.strange,\
  org.redfx.strange.gate,\
  org.redfx.strange.local,\
  org.redfx.strange.cloud,\
  org.redfx.strange.algorithm
# do NOT export org.redfx.strange.demo

# Pure JDK today → bnd computes Import-Package automatically (java.* is implicit).
# When cloud/ goes live, allow its optional deps to be missing at runtime:
Import-Package: \
  jakarta.json.*;resolution:=optional,\
  *
```

Mirror rule: **every package in OSGi `Export-Package` must equal a JPMS
`exports`** and vice-versa — keep WI-2 and WI-5 edited together. bnd can emit
both the OSGi manifest and (with `-jpms-module-info`) cross-check the module
descriptor, but hand-keeping two short lists is fine here.

### WI-6 — GitHub Actions CI (build + test matrix + native + release)

Create `/.github/workflows/ci.yml`:
```yaml
name: CI
on:
  push: { branches: [ main ], tags: [ 'v*' ] }
  pull_request: { branches: [ main ] }

jobs:
  build-test:
    name: JDK ${{ matrix.jdk }}
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        jdk: [ '17', '21', '25-ea' ]
    QuantumSteps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ matrix.jdk }}
          cache: maven
      - name: Build & test (module path)
        run: mvn -B -ntp verify

  native-image:
    name: Native image (GraalVM)
    runs-on: ubuntu-latest
    needs: build-test
    QuantumSteps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm'
          native-image-job-reports: 'true'
          github-token: ${{ secrets.GITHUB_TOKEN }}
      - name: Build native CLI
        run: mvn -B -ntp -Pnative package
      - name: Smoke-test the binary
        run: ./target/strange-cli      # runs Demo's main; must exit 0
      - uses: actions/upload-artifact@v4
        with: { name: strange-cli-linux, path: target/strange-cli }

  release:
    name: Publish to Maven Central
    runs-on: ubuntu-latest
    needs: [ build-test, native-image ]
    if: startsWith(github.ref, 'refs/tags/v')
    QuantumSteps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: maven
          server-id: central                  # Central Portal server id
          server-username: MAVEN_CENTRAL_USERNAME
          server-password: MAVEN_CENTRAL_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: GPG_PASSPHRASE
      - name: Deploy (strange + strange-bom)
        env:
          MAVEN_CENTRAL_USERNAME: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          MAVEN_CENTRAL_PASSWORD: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
        run: mvn -B -ntp -Prelease -DskipTests deploy
```

Notes:
- `25-ea` may be absent from Temurin until GA; allow it to fail without sinking
  the build via `fail-fast: false` plus (optionally) a `continue-on-error` QuantumStep,
  or source it from the `oracle-actions/setup-java` EA channel.
- The native smoke-test must invoke `Demo` in a **non-interactive** way and exit
  0; if `Demo.main` currently blocks or prints to stderr, add a `--smoke` arg
  guard when Section 10 reworks the CLI (do **not** edit it as part of this
  section — flag it as a dependency).

### WI-7 — Maven Central release setup (`release` profile + signing)

Central now uses the **Central Portal** publisher. Add a `release` profile to
root `pom.xml` (and inherit it in `bom/pom.xml`):
```xml
<profile>
  <id>release</id>
  <build>
    <plugins>
      <plugin>  <!-- sources jar -->
        <artifactId>maven-source-plugin</artifactId><version>3.3.1</version>
        <executions><execution><id>attach-sources</id>
          <goals><goal>jar-no-fork</goal></goals></execution></executions>
      </plugin>
      <plugin>  <!-- javadoc jar (Javadoc is rich in this codebase) -->
        <artifactId>maven-javadoc-plugin</artifactId><version>3.11.1</version>
        <executions><execution><id>attach-javadocs</id>
          <goals><goal>jar</goal></goals></execution></executions>
      </plugin>
      <plugin>  <!-- GPG sign all artifacts -->
        <artifactId>maven-gpg-plugin</artifactId><version>3.2.7</version>
        <executions><execution><id>sign-artifacts</id><phase>verify</phase>
          <goals><goal>sign</goal></goals></execution></executions>
      </plugin>
      <plugin>  <!-- Central Portal publishing -->
        <groupId>org.sonatype.central</groupId>
        <artifactId>central-publishing-maven-plugin</artifactId>
        <version>0.6.0</version>
        <extensions>true</extensions>
        <configuration>
          <publishingServerId>central</publishingServerId>
          <autoPublish>true</autoPublish>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```
Required GitHub secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`
(Portal user token), `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`. Central requires
sources jar, javadoc jar, GPG signatures, and complete POM metadata (licenses,
scm, developers) — all provided above. Verify `org.redfx` namespace ownership is
registered in the Portal.

---

## Testing Strategy

1. **Module-path compile.** `mvn -B -ntp compile` must succeed with
   `module-info.java` present — proves every `exports`/`requires` resolves and
   there are no split packages within `strange`.
2. **Unit tests on the module path.** `mvn verify` runs Surefire. Initially
   there are no tests (none exist today); add a trivial smoke test (build a
   2-qubit Bell `Program`, run `SimpleQuantumExecutionEnvironment`, assert
   probabilities) so CI has a green signal before Section 10's
   `QuantumAssert`/property tests arrive.
3. **Native image runs a sample.** CI's `native-image` job builds
   `target/strange-cli` and executes it; a non-zero exit fails the build. This
   is the real proof that reflection config is complete — a missing gate
   registration surfaces as a `ClassNotFoundException`/fallback at runtime.
4. **OSGi resolution check.** Add `bnd`'s `resolve`/`verify` (or a small
   `felix`/`Equinox` smoke) to confirm the bundle resolves with only JDK
   imports. At minimum, assert `Export-Package` ⊇ the JPMS `exports` set in a CI
   QuantumStep.
5. **Cross-JDK matrix green.** 17 and 21 must be green; 25-ea allowed to be
   advisory until GA.
6. **Dry-run release.** `mvn -Prelease -DskipTests verify` locally (or in a PR
   with a fake passphrase) to confirm sources/javadoc/signature artifacts are
   produced before the first real tag.

---

## Risks

1. **JPMS × OSGi × native-image triangle.** Each wants its own metadata for the
   same fact ("what does this jar export / instantiate reflectively"):
   - JPMS: `exports` / `provides`.
   - OSGi: `Export-Package` / `Import-Package` (bnd).
   - native-image: `reflect-config.json` / `serialization-config.json`.
   Mitigation: treat the **JPMS `exports` list as the single source of truth**,
   mirror it into bnd, and *generate* native-image config with the tracing
   agent. Keep WI-2/WI-5 edited together in every PR.
2. **Split packages.** If StrangeFX or StrangeEOM declare any
   `org.redfx.strange(.gate|.local|…)` package, the module path will refuse to
   load both. **Audit the siblings before the first modular release.** This is
   the highest-likelihood blocker because the three projects historically shared
   the `org.redfx.strange` root. The BOM does not fix split packages — only
   renaming/repackaging does.
3. **`getName() == getClass().getName()`.** Gate identity is the FQCN. Under
   native-image any class not in `reflect-config.json` can have its name elided
   or the class stripped → gate lookup/serialisation breaks silently. Enumerate
   **all** concrete gates (33 in `gate/` today) or rely on the agent; re-run the
   agent whenever gates are added (Section 1 adds S/T/iSWAP/Fredkin/…).
4. **`cloud/` is a stub.** Today it pulls in nothing; when Section 6 un-stubs it
   with `java.net.http` + `jakarta.json`, all three metadata sets change at once
   (module `requires`, OSGi optional imports, native-image reflection for JSON).
   Keep those deps **optional** (`requires static` / `resolution:=optional`) so
   the core jar stays dependency-free and native-image-friendly.
5. **`Classic` static backend field** prevents clean `ServiceLoader` discovery
   and is awkward under native-image (static initialisers run at build time by
   default). Recommend the SPI refactor (WI-2) — but that touches
   `algorithm/Classic.java`, which is **out of scope for this section**; flag it
   as a prerequisite owned by Section 3's API improvements.
6. **`25-ea` instability.** EA JDKs break native-image and occasionally the
   compiler. Keep it non-fatal.
7. **license-maven-plugin re-formatting.** The plugin can rewrite headers; run
   it in `check` mode in CI, not `format`, to avoid churning every file.
8. **Layout move (WI-0) breaks blame/PRs.** `git mv` preserves history but any
   in-flight branch from other sections will conflict. Do WI-0 **first**, before
   other sections start adding files.

---

## Suggested Sequencing

1. **WI-0** — move to `src/main/java` layout (do this before any other section
   adds files; pure `git mv`, no content edits).
2. **WI-1** — root `pom.xml` (classpath build + tests compile/run).
3. **Add one smoke test** (Bell state) so CI has a green target.
4. **WI-6 (build-test job only)** — get the JDK 17/21/25-ea matrix green on the
   classpath build first.
5. **WI-2** — `module-info.java`; fix any split-package fallout (Risk 2). Confirm
   module-path compile.
6. **WI-5** — bnd OSGi manifest mirroring the exports.
7. **WI-4** — native-image config (agent-generated) + `native` profile; wire the
   `native-image` CI job and its smoke-test.
8. **WI-3** — `strange-bom` (once `strange`'s own coordinates/version are
   settled; confirm StrangeFX/StrangeEOM groupIds first).
9. **WI-7 + WI-6 (release job)** — signing + Central Portal publishing on `v*`
   tags. Do a dry-run before the first real tag.

Land 1–4 as one PR (a working, tested, CI-covered classpath build), then 5–7 as
the "modular + native" PR, then 8–9 as the "publish" PR. This keeps each QuantumStep
independently green and isolates the risky split-package/native work.
