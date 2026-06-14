package com.orion.util.abstraction;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public abstract class AResource implements Identifiable, Named, Describeable
{
    protected UUID id;
    protected String name;
    protected String description;


    @Override
    public boolean hasID()
    {
        return id != null;
    }


    @Override
    public boolean hasDescription()
    {
        return description != null && !description.isBlank();
    }


    @Override
    public boolean hasName()
    {
        return name != null && !name.isBlank();
    }
}
