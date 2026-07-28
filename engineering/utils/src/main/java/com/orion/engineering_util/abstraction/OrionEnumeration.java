package com.orion.engineering_util.abstraction;

public interface OrionEnumeration
{
    String get();


    boolean is(OrionEnumeration other);


    boolean isNot(OrionEnumeration other);
}