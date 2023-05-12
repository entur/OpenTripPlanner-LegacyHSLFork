package org.opentripplanner.ext.transmodelapi;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opentripplanner.ext.actuator.MicrometerGraphQLInstrumentation;
import org.opentripplanner.ext.transmodelapi.support.OtpSimpleDataFetcherExceptionHandler;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.framework.concurrent.OtpRequestThreadFactory;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TransmodelGraph {

  private static final Logger LOG = LoggerFactory.getLogger(TransmodelGraph.class);
  private static final int MAX_ERROR_TO_RETURN = 10;
  private final GraphQLSchema indexSchema;

  final ExecutorService threadPool;

  TransmodelGraph(GraphQLSchema schema) {
    this.threadPool =
      Executors.newCachedThreadPool(OtpRequestThreadFactory.of("transmodel-api-%d"));
    this.indexSchema = schema;
  }

  ExecutionResult executeGraphQL(
    String query,
    OtpServerRequestContext serverContext,
    Map<String, Object> variables,
    String operationName,
    int maxResolves,
    Iterable<Tag> tracingTags
  ) {
    Instrumentation instrumentation = new MaxQueryComplexityInstrumentation(maxResolves);
    if (OTPFeature.ActuatorAPI.isOn()) {
      instrumentation =
        new ChainedInstrumentation(
          new MicrometerGraphQLInstrumentation(Metrics.globalRegistry, tracingTags),
          instrumentation
        );
    }

    GraphQL graphQL = GraphQL
      .newGraphQL(indexSchema)
      .defaultDataFetcherExceptionHandler(new OtpSimpleDataFetcherExceptionHandler())
      .instrumentation(instrumentation)
      .build();

    if (variables == null) {
      variables = new HashMap<>();
    }

    TransmodelRequestContext transmodelRequestContext = new TransmodelRequestContext(
      serverContext,
      serverContext.routingService(),
      serverContext.transitService()
    );

    ExecutionInput executionInput = ExecutionInput
      .newExecutionInput()
      .query(query)
      .operationName(operationName)
      .context(transmodelRequestContext)
      .root(serverContext)
      .variables(variables)
      .build();
    var result = graphQL.execute(executionInput);

    // Return 10 errors if there is more than 10 errors
    var errors = result.getErrors();
    if (errors.size() > MAX_ERROR_TO_RETURN) {
      final var errorsShortList = errors.stream().limit(MAX_ERROR_TO_RETURN).toList();
      LOG.warn(
        "Request failed with {} errors. Query='{}'",
        errors.size(),
        query.replaceAll("\\s+", " ")
      );
      result = result.transform(b -> b.errors(errorsShortList));
    }
    return result;
  }
}
