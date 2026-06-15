package com.orion.quantumcomputing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Qubits<T>
{
    private Map<T, Integer> qubits = new HashMap<>();


    public Qubits(T... keys)
    {
        for(T key : keys)
        {
            newBit(key);
        }
    }


    public int numberOfQubits()
    {
        return qubits.size();
    }


    public List<T> qubitKeys()
    {
        return List.copyOf(qubits.keySet());
    }


    public int newBit(T key)
    {
        if(qubits.containsKey(key))
        {
            throw new IllegalArgumentException("Qubit " + key + " already exists, use bit(key) to retrieve it.");
        }
        int index = qubits.size();
        qubits.put(key, index);
        return index;
    }


    public int bit(T key)
    {
        if(!qubits.containsKey(key))
        {
            throw new IllegalArgumentException("Qubit " + key + " isn't known, use newBit(key) to create it first.");
        }
        return qubits.get(key);
    }


    public QuantumProgram programOf(QuantumStep... QuantumSteps)
    {
        return new QuantumProgram(this.numberOfQubits(), QuantumSteps);
    }


    public Map<T, Qubit> getQubits(QuantumResult result)
    {
        Qubit[] qubits = result.getQubits();
        Map<T, Qubit> qubitMap = new HashMap<>();
        for(T key : qubitKeys())
        {
            qubitMap.put(key, qubits[bit(key)]);
        }
        return qubitMap;
    }


    public QuantumGate cnot(T a, T b)
    {
        return QuantumGate.cnot(bit(a), bit(b));
    }


    public QuantumStep cnotStep(T a, T b)
    {
        return new QuantumStep(cnot(a, b));
    }


    public QuantumGate cz(T a, T b)
    {
        return QuantumGate.cz(bit(a), bit(b));
    }


    public QuantumStep czStep(T a, T b)
    {
        return new QuantumStep(cz(a, b));
    }


    public QuantumGate hadamard(T q)
    {
        return QuantumGate.hadamard(bit(q));
    }


    public QuantumStep hadamardStep(T q)
    {
        return new QuantumStep(hadamard(q));
    }
}
