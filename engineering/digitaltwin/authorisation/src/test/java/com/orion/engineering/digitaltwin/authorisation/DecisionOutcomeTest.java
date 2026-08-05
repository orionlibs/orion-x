package com.orion.engineering.digitaltwin.authorisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionOutcomeTest
{
    @Test
    @DisplayName("When valueExists is called with a known value, then it returns true")
    void test1()
    {
        assertThat(DecisionOutcome.valueExists("Allow")).isTrue();
    }


    @Test
    @DisplayName("When valueExists is called with an unknown value, then it returns false")
    void test2()
    {
        assertThat(DecisionOutcome.valueExists("Unknown")).isFalse();
    }


    @Test
    @DisplayName("When getEnumForValue is called with a known value, then it returns the matching enum constant")
    void test3()
    {
        assertThat(DecisionOutcome.getEnumForValue("Deny")).isEqualTo(DecisionOutcome.Deny);
    }


    @Test
    @DisplayName("When is is called with the same enum constant, then it returns true")
    void test4()
    {
        assertThat(DecisionOutcome.Allow.is(DecisionOutcome.Allow)).isTrue();
    }


    @Test
    @DisplayName("When isNot is called with a different enum constant, then it returns true")
    void test5()
    {
        assertThat(DecisionOutcome.Deny.isNot(DecisionOutcome.Allow)).isTrue();
    }
}
