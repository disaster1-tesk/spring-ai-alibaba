package com.alibaba.cloud.ai.graph;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.node.llm.LLMNodeAction;
import com.alibaba.cloud.ai.graph.serializer.agent.JSONStateSerializer;
import com.alibaba.cloud.ai.graph.state.ReduceStrategy;
import com.alibaba.cloud.ai.graph.state.ReduceStrategyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.core.ParameterizedTypeReference;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

public class StateGraphTest {
    @Test
    public void nodeStateTest() {
        StateGraph stateGraph = new StateGraph(new JSONStateSerializer());
        RetrieverNodeAction retrieverNodeAction = new RetrieverNodeAction();
        LLMNodeAction llmNodeAction = new LLMNodeAction.Builder(new DashScopeChatModel(new DashScopeApi("sk-eba9e46456704338bf72c40342b1c9ad "))).build();
        CompiledGraph compiledGraph;
        try {
            stateGraph.addNode("code", retrieverNodeAction)
                    .addNode("llm", llmNodeAction)
                    .addEdge(START, "code")
                    .addEdge("code", "llm")
                    .addEdge("llm", END)
            ;
            compiledGraph = stateGraph.compile();
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
        try {
            MessageState messageState = (MessageState) compiledGraph
                    .invoke(Map.of("messages", new UserMessage("为什么dubbo3启动快10倍？")))
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @Data
    @NoArgsConstructor
    static class MessageState {

        @ReduceStrategy(value = ReduceStrategyType.APPEND_LIST)
        private List<Message> messages;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
   public static class RetrieverInputState {

        private String code;

        private String codeLanguage;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrieverOutputState {

        private List<Document> documents;

    }

    @Slf4j
   public static class RetrieverNodeAction implements AsyncNodeActionWithConfig<RetrieverInputState, RetrieverOutputState> {




        @Override
        public CompletableFuture<RetrieverOutputState> apply(RetrieverInputState retrieverInputState, RunnableConfig runnableConfig)  {
            List<Document> documents = List.of(new Document("GraalVM 能够提前将 Java 应用程序编译成独立的二进\n"
                    + "制文件。与在 Java 虚拟机上运行的应用程序相比，这些二进制文件更小，启动速度快 100\n" + "倍，在没有预热的情况下提供峰值性能，并且使用更少的内存和 CPU"));
            return CompletableFuture.supplyAsync(() -> new RetrieverOutputState(documents));
        }

        @Override
        public NodeActionDescriptor getNodeActionDescriptor() {
            return null;
        }

    }


    public static void main(String[] args) {
        /*
         *
         * 获取泛型类型：
         * 1、class.getGenericSuperclass(): 返回表示此Class表示的实体（类、接口、原始类型或 void）的直接超类的Type，如果它是
         * ParameterizedType实例，则可以通过它获取泛型类型。
         * 2、class.getTypeParameters(): 获取class的泛型类型
         */
        StateGraphTest genericActualType = new StateGraphTest();
//        genericActualType.printType();
//        genericActualType.genericSuperClassAndInterface();
//        genericActualType.printParent();
//        genericActualType.printSub();
//        genericActualType.printSub1();
        genericActualType.getActualGenericType();
//        genericActualType.parameterizedTypeRef();
    }


    public void getActualGenericType() {
        System.out.println("====> getActualGenericType: ");
        // List<String> list = new ArrayList<String>();
        List<String> list = new ArrayList<String>() {
        };
        BiFunction<String, Integer, Double> biFunction = new A();
        Type[] genericInterfaces = biFunction.getClass().getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                for (Type actualType : actualTypeArguments) {
                    System.out.println("Generic type: " + actualType.getTypeName());
                }
            }
        }
    }

    public class A implements BiFunction<String, Integer, Double>{

        @Override
        public Double apply(String s, Integer integer) {
            return 2.0;
        }
    }



}
