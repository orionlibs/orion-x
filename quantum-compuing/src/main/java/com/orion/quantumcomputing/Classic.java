package com.orion.quantumcomputing;

import java.util.List;
import java.util.function.Function;

public class Classic
{
    private static QuantumExecutor qee = new SimpleQuantumExecutor();


    public static void setQuantumExecutor(QuantumExecutor val)
    {
        qee = val;
    }


    public static int randomBit()
    {
        QuantumProgram program = new QuantumProgram(1, new QuantumStep(new Hadamard(0)));
        QuantumResult result = qee.runProgram(program);
        Qubit[] qubits = result.getQubits();
        return qubits[0].measure();
    }


    public static int qsum(int a, int b)
    {
        int y = a > b ? a : b;
        int x = a > b ? b : a;
        int m = y < 2 ? 1 : 1 + (int)Math.ceil(Math.log(y) / Math.log(2));
        int n = x < 2 ? 1 : 1 + (int)Math.ceil(Math.log(x) / Math.log(2));
        QuantumProgram program = new QuantumProgram(m + n);
        QuantumStep prep = new QuantumStep();
        int y0 = y;
        for(int i = 0; i < m; i++)
        {
            int p = 1 << (m - i - 1);
            if(y0 >= p)
            {
                prep.addGate(new X(m - i - 1));
                y0 = y0 - p;
            }
        }
        int x0 = x;
        for(int i = 0; i < n; i++)
        {
            int p = 1 << (n - i - 1);
            if(x0 >= p)
            {
                prep.addGate(new X(m + n - i - 1));
                x0 = x0 - p;
            }
        }
        program.addStep(prep);
        program.addStep(new QuantumStep(new Fourier(m, 0)));
        for(int i = 0; i < m; i++)
        {
            for(int j = 0; j < m - i; j++)
            {
                int cr0 = 2 * m - j - i - 1;
                if(cr0 < m + n)
                {
                    QuantumStep s = new QuantumStep(new Cr(i, cr0, 2, 1 + j));
                    program.addStep(s);
                }
            }
        }
        program.addStep(new QuantumStep(new InvFourier(m, 0)));
        QuantumResult res = qee.runProgram(program);
        Qubit[] qubits = res.getQubits();
        int answer = 0;
        for(int i = 0; i < m; i++)
        {
            if(qubits[i].measure() == 1)
            {
                answer = answer + (1 << i);
            }
        }
        return answer;
    }


    /**
     * Apply Grover's search algorithm to find the element from the supplied
     * list that would evaluate the provided function to 1
     *
     * @param <T> the type of the element
     * @param list the list of all elements that need to be searched into
     * @param function the function that, when evaluated, returns 0 for all
     * elements except for the element that this method returns, which evaluation
     * leads to 1.
     * @return the single element from the provided list that, when evaluated
     * by the function, returns 1.
     */
    public static <T> T search(List<T> list, Function<T, Integer> function)
    {
        double[] probability = searchProbabilities(list, function);
        int winner = 0;
        double wv = 0;
        for(int i = 0; i < probability.length; i++)
        {
            double a = probability[i];
            if(a > wv)
            {
                wv = a;
                winner = i;
            }
        }
        System.err.println("winner = " + winner + " with prob " + wv);
        return list.get(winner);
    }


    /**
     * Apply Grover's search algorithm to find the element from the supplied
     * list that would evaluate the provided function to 1
     *
     * @param <T> the type of the element
     * @param list the list of all elements that need to be searched into
     * @param function the function that, when evaluated, returns 0 for all
     * elements except for the element that this method returns, which evaluation
     * leads to 1.
     * @return the single element from the provided list that, when evaluated
     * by the function, returns 1.
     */
    public static <T> double[] searchProbabilities(List<T> list, Function<T, Integer> function)
    {
        int size = list.size();
        int n = (int)Math.ceil((Math.log(size) / Math.log(2)));
        int N = 1 << n;
        double cnt = Math.PI * Math.sqrt(N) / 4;
        Oracle oracle = createGroverOracle(n, list, function);
        QuantumProgram p = new QuantumProgram(n);
        QuantumStep s0 = new QuantumStep();
        for(int i = 0; i < n; i++)
        {
            s0.addGate(new Hadamard(i));
        }
        p.addStep(s0);
        oracle.setCaption("O");
        Complex[][] dif = createDiffMatrix(n);
        Oracle difOracle = new Oracle(dif);
        difOracle.setCaption("D");
        for(int i = 1; i < cnt; i++)
        {
            QuantumStep s1 = new QuantumStep("Oracle " + i);
            s1.addGate(oracle);
            QuantumStep s2 = new QuantumStep("Diffusion " + i);
            s2.addGate(difOracle);
            QuantumStep s3 = new QuantumStep("Prob " + i);
            s3.addGate(new ProbabilitiesGate(0));
            p.addStep(s1);
            p.addStep(s2);
            p.addStep(s3);
        }
        System.out.println(" n = " + n + ", QuantumSteps = " + cnt);
        QuantumResult res = qee.runProgram(p);
        Complex[] probability = res.getProbability();
        double[] answer = new double[probability.length];
        for(int i = 0; i < probability.length; i++)
        {
            answer[i] = probability[i].abssqr();
        }
        return answer;
    }


    /**
     * Find the periodicity of a^x mod N
     *
     * @param a a int
     * @param mod N
     * @return period r or -1 if no period is found
     */
    public static int findPeriod(int a, int mod)
    {
        int maxtries = 2;
        int tries = 0;
        int p = 0;
        while((p == 0) && tries < maxtries)
        {
            p = measurePeriod(a, mod);
            if(p == 0)
            {
                System.err.println("We measured a periodicity of 0, and have to start over.");
            }
        }
        if(p == 0)
        {
            return -1;
        }
        return Computations.fraction(p, mod);
    }


    public static int qfactor(int N)
    {
        System.out.println("We need to factor " + N);
        int a = 1 + (int)((N - 1) * Math.random());
        System.out.println("Pick a random number a, a < N: " + a);
        int gcdan = Computations.gcd(N, a);
        System.out.println("calculate gcd(a, N):" + gcdan);
        if(gcdan != 1)
        {
            return gcdan;
        }
        int p = findPeriod(a, N);
        if(p == -1)
        {
            System.err.println("After too many tries with " + a + ", we need to pick a new random number.");
            return qfactor(N);
        }
        System.out.println("period of f = " + p);
        if(p % 2 == 1)
        {
            System.out.println("bummer, odd period, restart.");
            return qfactor(N);
        }
        int md = (int)(Math.pow(a, p / 2) + 1);
        int m2 = md % N;
        if(m2 == 0)
        {
            System.out.println("bummer, m^p/2 + 1 = 0 mod N, restart");
            return qfactor(N);
        }
        int f2 = (int)Math.pow(a, p / 2) - 1;
        int factor = Computations.gcd(N, f2);
        return factor;
    }


    private static int measurePeriod(int a, int mod)
    {
        int length = (int)Math.ceil(Math.log(mod) / Math.log(2));
        int offset = length;
        QuantumProgram p = new QuantumProgram(2 * length + 2 + offset);
        QuantumStep prep = new QuantumStep();
        for(int i = 0; i < offset; i++)
        {
            prep.addGate(new Hadamard(i));
        }
        QuantumStep prepAnc = new QuantumStep(new X(offset));
        p.addStep(prep);
        p.addStep(prepAnc);
        for(int i = length - 1; i > length - 1 - offset; i--)
        {
            int m = 1;
            for(int j = 0; j < 1 << i; j++)
            {
                m = m * a % mod;
            }
            MulModulus mul = new MulModulus(length, 2 * length - 1, m, mod);
            ControlledBlockGate cbg = new ControlledBlockGate(mul, offset, i);
            p.addStep(new QuantumStep(cbg));
        }
        p.addStep(new QuantumStep(new InvFourier(offset, 0)));
        QuantumResult result = qee.runProgram(p);
        Qubit[] q = result.getQubits();
        int answer = 0;
        for(int i = 0; i < offset; i++)
        {
            answer = answer + q[i].measure() * (1 << i);
        }
        return answer;
    }


    private static <T> Oracle createGroverOracle(int n, List<T> list, Function<T, Integer> function)
    {
        int N = 1 << n;
        int listSize = list.size();
        Complex[][] matrix = new Complex[N][N];
        for(int i = 0; i < N; i++)
        {
            for(int j = 0; j < N; j++)
            {
                matrix[i][j] = (i != j) ? Complex.ZERO :
                                ((i >= listSize) || function.apply(list.get(i)) == 0) ?
                                Complex.ONE : Complex.ONE.mul(-1);
            }
        }
        return new Oracle(matrix);
    }


    private static Complex[][] createDiffMatrix(int n)
    {
        int N = 1 << n;
        QuantumGate g = new Hadamard(0);
        Complex[][] matrix = g.getMatrix();
        Complex[][] h2 = matrix;
        for(int i = 1; i < n; i++)
        {
            h2 = Complex.tensor(h2, matrix);
        }
        Complex[][] I2 = new Complex[N][N];
        for(int i = 0; i < N; i++)
        {
            for(int j = 0; j < N; j++)
            {
                if(i != j)
                {
                    I2[i][j] = Complex.ZERO;
                }
                else
                {
                    I2[i][j] = Complex.ONE;
                }
            }
        }
        I2[0][0] = Complex.ONE.mul(-1);
        int nd = n << 1;
        Complex[][] inter1 = Complex.mmul(h2, I2);
        Complex[][] dif = Complex.mmul(inter1, h2);
        return dif;
    }
}
