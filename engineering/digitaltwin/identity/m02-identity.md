# M02 Identity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a minimal, testable identity library (`com.orion:digital-twin-identity`) that models principals (device/service/human/agent), propagates the current principal across virtual-thread hops via `ScopedValue`, and authenticates a principal against a shared-secret credential store — the smallest slice of M02 that produces a real `Principal` for downstream modules to consume, deferring compliance-heavy mechanisms until the platform needs them.

**Architecture:** A plain Maven library (jar), following the exact convention already established by `mqtt/` and `engineering-utils/` in this repo — no Spring Boot parent, no `spring-boot-maven-plugin`, consumed later via dependency + component scan by whichever app needs it (per `digital-twin-platform-modules.md`'s Maven layout, M02 lives in `platform-security/`, which is explicitly *not* one of the four `apps/` deployables). Root package `com.orion.digital-twin-identity`, mirroring `com.orion.mqtt`.

**Tech Stack:** Java 26 (`ScopedValue`, finalized per JEP), JUnit 5, AssertJ. No Spring, no JWT/crypto library, no jackson — deliberately dependency-light for this iteration.

## Context

The full M02 spec (Spring Security 7/Spring Authorization Server, X.509 mTLS for high-capability devices, LoRaWAN OTAA join for constrained devices, OIDC federation for humans, cert/key rotation and fast isolation of compromised devices) describes the *mature* version of this module. The user explicitly asked to keep this iteration simple — build a working digital twin platform first, harden identity later. This plan implements only:
- A `Principal` model covering all four principal types (device/service/human/agent).
- `ScopedValue`-based propagation of the authenticated principal (the one JVM-25-specific mechanism called out in the spec that costs nothing to build now and is needed regardless of how auth eventually gets stronger).
- A single shared-secret authentication mechanism, standing in for X.509/mTLS/OIDC until those are actually needed.

**Explicitly deferred (not in this plan):** X.509 client certs + mutual TLS, LoRaWAN OTAA join, OIDC human federation, Spring Authorization Server, key/cert rotation and renewal, compromised-device isolation. These become their own follow-up plans once the platform has real devices/humans to authenticate.

## Global Constraints

- Packaging: plain jar library (`<packaging>jar</packaging>`), no `<parent>spring-boot-starter-parent</parent>`, no `spring-boot-maven-plugin` — matches `mqtt/pom.xml` exactly.
- `groupId` `com.orion`, `artifactId` `digital-twin-identity`, root Java package `com.orion.engineering.digitaltwin.identity`.
- Java 26 (`maven.compiler.source/target` = 26, `release` = 26) — matches every sibling module.
- Every JUnit test class uses `@DisplayName` with the exact pattern `"When ..., then ..."`.
- Every assertion statement is written on one line (no multi-line AssertJ chains).
- Chained method calls that span multiple lines have their `.` vertically aligned (per repo convention already visible in `MQTTAsynchronousPublisherClient`).
- Reuse `com.orion.engineering_util.abstraction.OrionEnumeration` for the new enum, mirroring `DataSourceType` in the `mqtt` module exactly (same method shapes: `valueExists`, `getEnumForValue`, `get`, `is`, `isNot`).
- TDD: write the failing test before the implementation for every class.

---

## File Structure

**Delete** (leftover copy-paste from `sensor-simulator`, not identity-related):
- `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/` (entire tree — app class + pressure/temperature sensor simulators)
- `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/` (entire tree)
- `engineering/digitaltwin/identity/src/main/resources/application.yml`
- `engineering/digitaltwin/identity/src/main/resources/application-local.yml`
- `engineering/digitaltwin/identity/src/test/resources/application-test.yml`

**Rewrite:**
- `engineering/digitaltwin/identity/pom.xml` — from Spring-Boot-app shape to plain-library shape (mirrors `mqtt/pom.xml`).

**Create:**
- `src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalType.java` — enum: `Device`, `Service`, `Human`, `Agent`.
- `src/main/java/com/orion/engineering/digitaltwin/identity/Principal.java` — record: `id`, `type`, `roles`.
- `src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalContext.java` — `ScopedValue<Principal>` holder + `runAs`/`callAs`/`current`.
- `src/main/java/com/orion/engineering/digitaltwin/identity/credential/Credential.java` — record: `principalId`, `type`, `sharedSecret`, `roles`.
- `src/main/java/com/orion/engineering/digitaltwin/identity/credential/CredentialStore.java` — interface: `findByPrincipalId`.
- `src/main/java/com/orion/engineering/digitaltwin/identity/credential/InMemoryCredentialStore.java` — map-backed implementation.
- `src/main/java/com/orion/engineering/digitaltwin/identity/auth/AuthenticationException.java` — unchecked exception.
- `src/main/java/com/orion/engineering/digitaltwin/identity/auth/SharedSecretAuthenticator.java` — looks up credential, constant-time-compares secret, returns `Principal`.
- Matching test classes under `src/test/java/com/orion/engineering/digitaltwin/identity/...` for every class above.

**Interfaces (for cross-task reference):**
- `Principal(String id, PrincipalType type, Set<String> roles)`
- `PrincipalContext.runAs(Principal principal, Runnable action): void`
- `PrincipalContext.callAs(Principal principal, Callable<T> action): T throws Exception`
- `PrincipalContext.current(): Optional<Principal>`
- `Credential(String principalId, PrincipalType type, String sharedSecret, Set<String> roles)`
- `CredentialStore.findByPrincipalId(String principalId): Optional<Credential>`
- `SharedSecretAuthenticator.authenticate(String principalId, String presentedSecret): Principal` (throws `AuthenticationException`)

---

### Task 1: Reset the module skeleton

**Files:**
- Delete: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/` (recursively)
- Delete: `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/` (recursively)
- Delete: `engineering/digitaltwin/identity/src/main/resources/application.yml`, `application-local.yml`
- Delete: `engineering/digitaltwin/identity/src/test/resources/application-test.yml`
- Modify: `engineering/digitaltwin/identity/pom.xml`

- [ ] **Step 1: Delete the leftover sensor-simulator source trees and resource files**

```bash
rm -rf engineering/digitaltwin/identity/src/main/java/com/orion/engineering
rm -rf engineering/digitaltwin/identity/src/test/java/com/orion/engineering
rm -f engineering/digitaltwin/identity/src/main/resources/application.yml
rm -f engineering/digitaltwin/identity/src/main/resources/application-local.yml
rm -f engineering/digitaltwin/identity/src/test/resources/application-test.yml
```

- [ ] **Step 2: Rewrite `pom.xml` to the plain-library shape**

Replace the full contents of `engineering/digitaltwin/identity/pom.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi = "http://www.w3.org/2001/XMLSchema-instance" xmlns = "http://maven.apache.org/POM/4.0.0"
  xsi:schemaLocation = "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.orion</groupId>
    <artifactId>digital-twin-identity</artifactId>
    <version>0.0.1</version>
    <name>Orion Engineering Digital Twin Identity</name>
    <packaging>jar</packaging>


    <properties>
        <maven.compiler.source>26</maven.compiler.source>
        <maven.compiler.target>26</maven.compiler.target>
        <java.version>26</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    </properties>


    <build>
        <finalName>digital-twin-identity</finalName>


        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <compilerArgs>
                        <arg>-J--add-opens=java.base/java.time=ALL-UNNAMED</arg>
                        <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
                        <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
                        <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
                        <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
                        <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
                    </compilerArgs>


                    <encoding>UTF-8</encoding>
                    <fork>true</fork>

                    <release>${maven.compiler.target}</release>
                </configuration>
                <groupId>org.apache.maven.plugins</groupId>
                <version>3.15.0</version>
            </plugin>


            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables>
                        <java.locale.providers>COMPAT,CLDR,SPI,JRE</java.locale.providers>
                    </systemPropertyVariables>
                </configuration>
                <groupId>org.apache.maven.plugins</groupId>


                <version>3.5.6</version>
            </plugin>
        </plugins>


        <resources>
            <resource>
                <directory>src/main/resources</directory>


                <includes>
                    <include>**/*</include>
                </includes>
            </resource>
        </resources>


        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
            </testResource>


            <testResource>
                <directory>${project.basedir}/src/test/java</directory>
            </testResource>
        </testResources>
    </build>


    <dependencyManagement>
        <dependencies>
            <dependency>
                <artifactId>junit-bom</artifactId>
                <groupId>org.junit</groupId>
                <scope>import</scope>
                <type>pom</type>
                <version>6.0.1</version>
            </dependency>
        </dependencies>
    </dependencyManagement>


    <dependencies>
        <dependency>
            <groupId>com.orion</groupId>
            <artifactId>engineering-utils</artifactId>
            <version>0.0.1</version>
        </dependency>


        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.17</version>
        </dependency>


        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>


        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-suite-engine</artifactId>
        </dependency>


        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.27.6</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Verify the module still builds with no source files**

Run: `cd engineering/digitaltwin/identity && mvn -q compile`
Expected: `BUILD SUCCESS` (no sources yet, nothing to compile).

- [ ] **Step 4: Commit**

```bash
git add engineering/digitaltwin/identity/pom.xml
git add -u engineering/digitaltwin/identity/src
git commit -m "chore(identity): reset module skeleton to plain-library shape"
```

---

### Task 2: Principal model — `PrincipalType` + `Principal`

**Files:**
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalType.java`
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/Principal.java`
- Test: `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/PrincipalTypeTest.java`

**Interfaces:**
- Produces: `PrincipalType` (enum: `Device`, `Service`, `Human`, `Agent`), `Principal(String id, PrincipalType type, Set<String> roles)` — both are used by every later task.

- [ ] **Step 1: Write the failing test for `PrincipalType`**

```java
package com.orion.engineering.digitaltwin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrincipalTypeTest
{
    @Test
    @DisplayName("When valueExists is called with a known value, then it returns true")
    void test1()
    {
        assertThat(PrincipalType.valueExists("Device")).isTrue();
    }


    @Test
    @DisplayName("When valueExists is called with an unknown value, then it returns false")
    void test2()
    {
        assertThat(PrincipalType.valueExists("Unknown")).isFalse();
    }


    @Test
    @DisplayName("When getEnumForValue is called with a known value, then it returns the matching enum constant")
    void test3()
    {
        assertThat(PrincipalType.getEnumForValue("Service")).isEqualTo(PrincipalType.Service);
    }


    @Test
    @DisplayName("When is is called with the same enum constant, then it returns true")
    void test4()
    {
        assertThat(PrincipalType.Human.is(PrincipalType.Human)).isTrue();
    }


    @Test
    @DisplayName("When isNot is called with a different enum constant, then it returns true")
    void test5()
    {
        assertThat(PrincipalType.Agent.isNot(PrincipalType.Device)).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=PrincipalTypeTest`
Expected: FAIL — `PrincipalType` does not exist (compilation error).

- [ ] **Step 3: Write `PrincipalType`**

```java
package com.orion.engineering.digitaltwin.identity;

import com.orion.engineering_util.abstraction.OrionEnumeration;

public enum PrincipalType implements OrionEnumeration
{
    Device("Device"),
    Service("Service"),
    Human("Human"),
    Agent("Agent");
    private String name;


    private PrincipalType(String name)
    {
        setName(name);
    }


    public static boolean valueExists(String other)
    {
        PrincipalType[] values = values();
        for(PrincipalType value : values)
        {
            if(value.get().equals(other))
            {
                return true;
            }
        }
        return false;
    }


    public static PrincipalType getEnumForValue(String other)
    {
        PrincipalType[] values = values();
        for(PrincipalType value : values)
        {
            if(value.get().equals(other))
            {
                return value;
            }
        }
        return null;
    }


    @Override
    public String get()
    {
        return getName();
    }


    public String getName()
    {
        return this.name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    @Override
    public boolean is(OrionEnumeration other)
    {
        return other instanceof PrincipalType && this == other;
    }


    @Override
    public boolean isNot(OrionEnumeration other)
    {
        return other instanceof PrincipalType && this != other;
    }
}
```

- [ ] **Step 4: Write `Principal` (no test — plain record, exercised by later tasks' tests)**

```java
package com.orion.engineering.digitaltwin.identity;

import java.util.Set;

public record Principal(String id, PrincipalType type, Set<String> roles)
{
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=PrincipalTypeTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalType.java
git add engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/Principal.java
git add engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/PrincipalTypeTest.java
git commit -m "feat(identity): add Principal and PrincipalType model"
```

---

### Task 3: `PrincipalContext` — ScopedValue propagation

**Files:**
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalContext.java`
- Test: `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/PrincipalContextTest.java`

**Interfaces:**
- Consumes: `Principal` (Task 2).
- Produces: `PrincipalContext.runAs(Principal, Runnable): void`, `PrincipalContext.callAs(Principal, Callable<T>): T throws Exception`, `PrincipalContext.current(): Optional<Principal>` — later tasks do not call these directly, but this is the mechanism the spec calls out for cross-thread identity propagation.

- [ ] **Step 1: Write the failing test**

```java
package com.orion.engineering.digitaltwin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrincipalContextTest
{
    @Test
    @DisplayName("When no principal has been bound, then current returns empty")
    void test1()
    {
        assertThat(PrincipalContext.current()).isEmpty();
    }


    @Test
    @DisplayName("When runAs binds a principal, then current returns that principal inside the scope")
    void test2()
    {
        Principal principal = new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish"));
        PrincipalContext.runAs(principal, () -> assertThat(PrincipalContext.current()).contains(principal));
    }


    @Test
    @DisplayName("When runAs completes, then current returns empty outside the scope")
    void test3()
    {
        Principal principal = new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish"));
        PrincipalContext.runAs(principal, () -> {});
        assertThat(PrincipalContext.current()).isEmpty();
    }


    @Test
    @DisplayName("When callAs binds a principal, then current returns that principal inside the call and the return value passes through")
    void test4() throws Exception
    {
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of());
        String result = PrincipalContext.callAs(principal, () -> PrincipalContext.current().orElseThrow().id());
        assertThat(result).isEqualTo("service-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=PrincipalContextTest`
Expected: FAIL — `PrincipalContext` does not exist (compilation error).

- [ ] **Step 3: Write `PrincipalContext`**

```java
package com.orion.engineering.digitaltwin.identity;

import java.util.Optional;
import java.util.concurrent.Callable;

public final class PrincipalContext
{
    private static final ScopedValue<Principal> CURRENT_PRINCIPAL = ScopedValue.newInstance();


    private PrincipalContext()
    {
    }


    public static void runAs(Principal principal, Runnable action)
    {
        ScopedValue.where(CURRENT_PRINCIPAL, principal)
                   .run(action);
    }


    public static <T> T callAs(Principal principal, Callable<T> action) throws Exception
    {
        return ScopedValue.where(CURRENT_PRINCIPAL, principal)
                           .call(action);
    }


    public static Optional<Principal> current()
    {
        return CURRENT_PRINCIPAL.isBound() ? Optional.of(CURRENT_PRINCIPAL.get()) : Optional.empty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=PrincipalContextTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/PrincipalContext.java
git add engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/PrincipalContextTest.java
git commit -m "feat(identity): add ScopedValue-based PrincipalContext propagation"
```

---

### Task 4: Credential model + `CredentialStore` + `InMemoryCredentialStore`

**Files:**
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/credential/Credential.java`
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/credential/CredentialStore.java`
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/credential/InMemoryCredentialStore.java`
- Test: `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/credential/InMemoryCredentialStoreTest.java`

**Interfaces:**
- Consumes: `PrincipalType` (Task 2).
- Produces: `Credential(String principalId, PrincipalType type, String sharedSecret, Set<String> roles)`, `CredentialStore.findByPrincipalId(String): Optional<Credential>`, `InMemoryCredentialStore(Collection<Credential>)` — Task 5 depends on all three.

- [ ] **Step 1: Write the failing test**

```java
package com.orion.engineering.digitaltwin.identity.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.orion.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCredentialStoreTest
{
    @Test
    @DisplayName("When a credential exists for a principal id, then findByPrincipalId returns it")
    void test1()
    {
        Credential credential = new Credential("device-1", PrincipalType.Device, "s3cr3t", Set.of("telemetry:publish"));
        InMemoryCredentialStore store = new InMemoryCredentialStore(List.of(credential));
        assertThat(store.findByPrincipalId("device-1")).contains(credential);
    }


    @Test
    @DisplayName("When no credential exists for a principal id, then findByPrincipalId returns empty")
    void test2()
    {
        InMemoryCredentialStore store = new InMemoryCredentialStore(List.of());
        assertThat(store.findByPrincipalId("unknown")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=InMemoryCredentialStoreTest`
Expected: FAIL — `Credential`/`InMemoryCredentialStore` do not exist (compilation error).

- [ ] **Step 3: Write `Credential`**

```java
package com.orion.engineering.digitaltwin.identity.credential;

import com.orion.identity.PrincipalType;
import java.util.Set;

public record Credential(String principalId, PrincipalType type, String sharedSecret, Set<String> roles)
{
}
```

- [ ] **Step 4: Write `CredentialStore`**

```java
package com.orion.engineering.digitaltwin.identity.credential;

import java.util.Optional;

public interface CredentialStore
{
    Optional<Credential> findByPrincipalId(String principalId);
}
```

- [ ] **Step 5: Write `InMemoryCredentialStore`**

```java
package com.orion.engineering.digitaltwin.identity.credential;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InMemoryCredentialStore implements CredentialStore
{
    private final Map<String, Credential> credentialsByPrincipalId;


    public InMemoryCredentialStore(Collection<Credential> credentials)
    {
        this.credentialsByPrincipalId = credentials.stream()
                                                     .collect(Collectors.toMap(Credential::principalId, Function.identity()));
    }


    @Override
    public Optional<Credential> findByPrincipalId(String principalId)
    {
        return Optional.ofNullable(credentialsByPrincipalId.get(principalId));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=InMemoryCredentialStoreTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/credential
git add engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/credential
git commit -m "feat(identity): add Credential model and InMemoryCredentialStore"
```

---

### Task 5: `SharedSecretAuthenticator` + `AuthenticationException`

**Files:**
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/auth/AuthenticationException.java`
- Create: `engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/auth/SharedSecretAuthenticator.java`
- Test: `engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/auth/SharedSecretAuthenticatorTest.java`

**Interfaces:**
- Consumes: `Principal`, `PrincipalType` (Task 2); `Credential`, `CredentialStore`, `InMemoryCredentialStore` (Task 4).
- Produces: `SharedSecretAuthenticator.authenticate(String principalId, String presentedSecret): Principal` (throws `AuthenticationException`) — this is the module's externally consumable entry point for this iteration; later modules (M03) receive its return value.

- [ ] **Step 1: Write the failing test**

```java
package com.orion.engineering.digitaltwin.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orion.identity.Principal;
import com.orion.identity.PrincipalType;
import com.orion.identity.credential.Credential;
import com.orion.identity.credential.InMemoryCredentialStore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedSecretAuthenticatorTest
{
    private SharedSecretAuthenticator authenticator;


    @BeforeEach
    void setUp()
    {
        Credential credential = new Credential("device-1", PrincipalType.Device, "s3cr3t", Set.of("telemetry:publish"));
        authenticator = new SharedSecretAuthenticator(new InMemoryCredentialStore(List.of(credential)));
    }


    @Test
    @DisplayName("When the presented secret matches the stored secret, then authenticate returns the principal")
    void test1()
    {
        Principal principal = authenticator.authenticate("device-1", "s3cr3t");
        assertThat(principal).isEqualTo(new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish")));
    }


    @Test
    @DisplayName("When the principal id is unknown, then authenticate throws AuthenticationException")
    void test2()
    {
        assertThatThrownBy(() -> authenticator.authenticate("unknown", "s3cr3t")).isInstanceOf(AuthenticationException.class);
    }


    @Test
    @DisplayName("When the presented secret does not match, then authenticate throws AuthenticationException")
    void test3()
    {
        assertThatThrownBy(() -> authenticator.authenticate("device-1", "wrong")).isInstanceOf(AuthenticationException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=SharedSecretAuthenticatorTest`
Expected: FAIL — `AuthenticationException`/`SharedSecretAuthenticator` do not exist (compilation error).

- [ ] **Step 3: Write `AuthenticationException`**

```java
package com.orion.engineering.digitaltwin.identity.auth;

public class AuthenticationException extends RuntimeException
{
    public AuthenticationException(String message)
    {
        super(message);
    }
}
```

- [ ] **Step 4: Write `SharedSecretAuthenticator`**

```java
package com.orion.engineering.digitaltwin.identity.auth;

import com.orion.identity.Principal;
import com.orion.identity.credential.Credential;
import com.orion.identity.credential.CredentialStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SharedSecretAuthenticator
{
    private final CredentialStore credentialStore;


    public SharedSecretAuthenticator(CredentialStore credentialStore)
    {
        this.credentialStore = credentialStore;
    }


    public Principal authenticate(String principalId, String presentedSecret)
    {
        Credential credential = credentialStore.findByPrincipalId(principalId)
                                                 .orElseThrow(() -> new AuthenticationException("Unknown principal: " + principalId));
        if(!secretsMatch(credential.sharedSecret(), presentedSecret))
        {
            throw new AuthenticationException("Invalid credentials for principal: " + principalId);
        }
        return new Principal(credential.principalId(), credential.type(), credential.roles());
    }


    private boolean secretsMatch(String expected, String presented)
    {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd engineering/digitaltwin/identity && mvn -q test -Dtest=SharedSecretAuthenticatorTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Run the full module test suite**

Run: `cd engineering/digitaltwin/identity && mvn -q test`
Expected: `BUILD SUCCESS`, all tests across the module pass (14 tests total).

- [ ] **Step 7: Commit**

```bash
git add engineering/digitaltwin/identity/src/main/java/com/orion/engineering/digitaltwin/identity/auth
git add engineering/digitaltwin/identity/src/test/java/com/orion/engineering/digitaltwin/identity/auth
git commit -m "feat(identity): add SharedSecretAuthenticator"
```

---

## Future Work (explicitly out of scope for this plan)

- Replace shared-secret device auth with X.509 client certs + mutual TLS for high-capability devices (App. B).
- Add LoRaWAN OTAA join (`DevEUI`/`AppEUI`/`AppKey`) for constrained LPWAN devices (App. A.6).
- OIDC federation for human principals, with role separation (Spring Authorization Server or an external IdP).
- Key/cert lifecycle: rotation, renewal before expiry, fast isolation of a compromised device (§3.6).
- Wire this library into an actual app deployable (e.g. `app-ingest`) once one exists, including mTLS termination at the broker/gateway (`M11`) and inward propagation of the verified identity via `PrincipalContext`.
- Replace `InMemoryCredentialStore` with a real backing store once `M08` (device registry) exists.

## Verification

After all 5 tasks: `cd engineering/digitaltwin/identity && mvn -q test` should report `BUILD SUCCESS` with 0 failures across `PrincipalTypeTest`, `PrincipalContextTest`, `InMemoryCredentialStoreTest`, and `SharedSecretAuthenticatorTest`.

## Deliverable for this turn

Per the user's request, this plan document itself is written to `engineering/digitaltwin/identity/m02-identity.md` — no code is changed yet. Implementation (Tasks 1–5 above) happens only when the user explicitly asks to execute this plan.
