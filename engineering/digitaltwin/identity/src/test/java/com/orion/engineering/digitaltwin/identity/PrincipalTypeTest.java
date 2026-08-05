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
