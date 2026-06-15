package com.orion.quantumcomputing.cloud;

//import com.gluonhq.connect.converter.InputStreamInputConverter;

public class QuantumResultConverter
{
    //        extends InputStreamInputConverter<Result> {
    //
    //    @Override
    //    public Result read() {
    //        List<Qubit> qubits = new ArrayList<>();
    //        InputStreamReader reader = null;
    //        try {
    //            reader = new InputStreamReader(getInputStream());
    //            StringBuilder b = new StringBuilder();
    //            char[] buffer = new char[4096];
    //            int read;
    //            while ((read = reader.read(buffer, 0, 4096)) != -1) {
    //                b.append(buffer, 0, read);
    //            }
    //
    //            System.out.println("result = " + b.toString());
    //
    //            JsonReader jsonReader = Json.createReader(new StringReader(b.toString()));
    //            JsonObject jsonResult = jsonReader.readObject();
    //            JsonValue jsonQubits = jsonResult.get("qubits");
    //            if (jsonQubits.getValueType() == JsonValue.ValueType.ARRAY) {
    //                jsonResult.getJsonArray("qubits").getValuesAs(JsonNumber.class)
    //                        .stream()
    //                        .map(probability -> {
    //                            Qubit qubit = new Qubit();
    //                            qubit.setProbability(probability.doubleValue());
    //                            return qubit;
    //                        }).forEach(qubits::add);
    //            }
    //        } catch (IOException e) {
    //            e.printStackTrace();
    //        } finally {
    //            if (reader != null) {
    //                try {
    //                    reader.close();
    //                } catch (IOException e) {
    //                    e.printStackTrace();
    //                }
    //            }
    //        }
    //
    //        return new Result(qubits.toArray(new Qubit[0]), new Complex[0]);
    //    }
}
