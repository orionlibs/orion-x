package com.orion.quantumcomputing.cloud;

public class CloudQuantumExecutor
{
    //        implements QuantumExecutionEnvironment {
    //
    //    private final String functionName;
    //
    //    public CloudlinkQuantumExecutionEnvironment(String functionName) {
    //        this.functionName = functionName;
    //    }
    //
    //    @Override
    //    public Result runProgram(Program p) {
    //        Future<Result> f = doRunProgram(p);
    //        try {
    //            return f.get();
    //        } catch (Throwable t) {
    //            t.printStackTrace();
    //        }
    //        return null;
    //    }
    //
    //    @Override
    //    public void runProgram(Program p, Consumer<Result> resultConsumer) {
    //        String serializedProgram = serializeProgram(p).toString();
    //
    //        CompletableFuture<Result> futureResult = new CompletableFuture<>();
    //
    //        GluonObservableObject<Result> result = RemoteFunctionBuilder.create(functionName)
    //                .param("program", serializedProgram)
    //                .cachingEnabled(false)
    //                .object()
    //                .call(new ResultConverter());
    //         result.stateProperty().addListener((obs, ov, nv) -> {
    //            if (nv == ConnectState.SUCCEEDED) {
    //                resultConsumer.accept(result.get());
    //            }
    //         });
    //    }
    //
    //    private Future<Result> doRunProgram(Program p) {
    //        String serializedProgram = serializeProgram(p).toString();
    //
    //        CompletableFuture<Result> futureResult = new CompletableFuture<>();
    //
    //        GluonObservableObject<Result> result = RemoteFunctionBuilder.create(functionName)
    //                .param("program", serializedProgram)
    //                .cachingEnabled(false)
    //                .object()
    //                .call(new ResultConverter());
    //
    //        result.stateProperty().addListener((obs, ov, nv) -> {
    //            if (nv == ConnectState.SUCCEEDED) {
    //                futureResult.complete(result.get());
    //            } else if (nv == ConnectState.FAILED) {
    //                futureResult.completeExceptionally(result.getException());
    //            } else if (nv == ConnectState.CANCELLED) {
    //                futureResult.cancel(true);
    //            }
    //        });
    //
    //        return futureResult;
    //    }
    //
    //    private JsonObject serializeProgram(Program program) {
    //        JsonArrayBuilder jsonSteps = Json.createArrayBuilder();
    //        program.getSteps().stream()
    //                .map(this::serializeStep)
    //                .forEach(jsonSteps::add);
    //
    //        return Json.createObjectBuilder()
    //                .add("numberQubits", program.getNumberQubits())
    //                .add("steps", jsonSteps.build())
    //                .build();
    //    }
    //
    //    private JsonObject serializeStep(Step QuantumStep) {
    //        JsonArrayBuilder jsonGates = Json.createArrayBuilder();
    //        QuantumStep.getGates().stream()
    //                .map(this::serializeGate)
    //                .forEach(jsonGates::add);
    //
    //        return Json.createObjectBuilder()
    //                .add("gates", jsonGates.build())
    //                .build();
    //    }
    //
    //    private JsonObject serializeGate(Gate gate) {
    //        JsonArrayBuilder jsonAffectedQubitIndex = Json.createArrayBuilder();
    //        gate.getAffectedQubitIndex().forEach(jsonAffectedQubitIndex::add);
    //
    //        return Json.createObjectBuilder()
    //                .add("caption", gate.getCaption())
    //                .add("group", gate.getGroup())
    //                .add("affectedQubitIndex", jsonAffectedQubitIndex.build())
    //                .build();
    //    }
}
