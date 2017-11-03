/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.networking;

import org.cloudfoundry.AbstractIntegrationTest;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest;
import org.cloudfoundry.client.v2.applications.CreateApplicationResponse;
import org.cloudfoundry.networking.policies.CreatePoliciesRequest;
import org.cloudfoundry.networking.policies.DeletePoliciesRequest;
import org.cloudfoundry.networking.policies.Destination;
import org.cloudfoundry.networking.policies.ListPoliciesRequest;
import org.cloudfoundry.networking.policies.ListPoliciesResponse;
import org.cloudfoundry.networking.policies.Policy;
import org.cloudfoundry.networking.policies.Ports;
import org.cloudfoundry.networking.policies.Source;
import org.cloudfoundry.util.ResourceUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.cloudfoundry.util.tuple.TupleUtils.function;

public final class PoliciesTest extends AbstractIntegrationTest {

    @Autowired
    private CloudFoundryClient cloudFoundryClient;

    @Autowired
    private NetworkingClient networkingClient;

    @Autowired
    private Mono<String> spaceId;

    @Test
    public void create() throws TimeoutException, InterruptedException {
        String destinationApplicationName = this.nameFactory.getApplicationName();
        String sourceApplicationName = this.nameFactory.getApplicationName();
        Integer port = this.nameFactory.getPort();

        this.spaceId
            .then(spaceId -> Mono.when(
                createApplicationId(this.cloudFoundryClient, destinationApplicationName, spaceId),
                createApplicationId(this.cloudFoundryClient, sourceApplicationName, spaceId)
            ))
            .then(function((destinationApplicationId, sourceApplicationId) -> this.networkingClient.policies()
                .create(CreatePoliciesRequest.builder()
                    .policy(Policy.builder()
                        .destination(Destination.builder()
                            .id(destinationApplicationId)
                            .ports(Ports.builder()
                                .end(port + 1)
                                .start(port)
                                .build())
                            .protocol("tcp")
                            .build())
                        .source(Source.builder()
                            .id(sourceApplicationId)
                            .build())
                        .build())
                    .build())
                .then(Mono.just(destinationApplicationId))))
            .then(destinationApplicationId -> requestListPolicies(this.networkingClient)
                .flatMapIterable(ListPoliciesResponse::getPolicies)
                .filter(policy -> destinationApplicationId.equals(policy.getDestination().getId()))
                .single())
            .map(policy -> policy.getDestination().getPorts().getStart())
            .as(StepVerifier::create)
            .expectNext(port)
            .expectComplete()
            .verify(Duration.ofMinutes(5));
    }

    @Test
    public void delete() throws TimeoutException, InterruptedException {
        String destinationApplicationName = this.nameFactory.getApplicationName();
        String sourceApplicationName = this.nameFactory.getApplicationName();
        Integer port = this.nameFactory.getPort();

        this.spaceId
            .then(spaceId -> Mono.when(
                createApplicationId(this.cloudFoundryClient, destinationApplicationName, spaceId),
                createApplicationId(this.cloudFoundryClient, sourceApplicationName, spaceId)
            ))
            .then(function((destinationApplicationId, sourceApplicationId) -> requestCreatePolicy(this.networkingClient, destinationApplicationId, port, sourceApplicationId)
                .then(Mono.just(Tuples.of(destinationApplicationId, sourceApplicationId)))))
            .then(function((destinationApplicationId, sourceApplicationId) -> this.networkingClient.policies()
                .delete(DeletePoliciesRequest.builder()
                    .policy(Policy.builder()
                        .destination(Destination.builder()
                            .id(destinationApplicationId)
                            .ports(Ports.builder()
                                .end(port)
                                .start(port)
                                .build())
                            .protocol("tcp")
                            .build())
                        .source(Source.builder()
                            .id(sourceApplicationId)
                            .build())
                        .build())
                    .build())))
            .then(requestListPolicies(this.networkingClient)
                .flatMapIterable(ListPoliciesResponse::getPolicies)
                .filter(policy -> port.equals(policy.getDestination().getPorts().getStart()))
                .singleOrEmpty())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofMinutes(5));
    }

    @Test
    public void list() throws TimeoutException, InterruptedException {
        String destinationApplicationName = this.nameFactory.getApplicationName();
        String sourceApplicationName = this.nameFactory.getApplicationName();
        Integer port = this.nameFactory.getPort();

        this.spaceId
            .then(spaceId -> Mono.when(
                createApplicationId(this.cloudFoundryClient, destinationApplicationName, spaceId),
                createApplicationId(this.cloudFoundryClient, sourceApplicationName, spaceId)
            ))
            .then(function((destinationApplicationId, sourceApplicationId) -> requestCreatePolicy(this.networkingClient, destinationApplicationId, port, sourceApplicationId)))
            .then(this.networkingClient.policies()
                .list(ListPoliciesRequest.builder()
                    .build())
                .flatMapIterable(ListPoliciesResponse::getPolicies)
                .filter(policy -> port.equals(policy.getDestination().getPorts().getStart()))
                .single())
            .map(policy -> policy.getDestination().getPorts().getStart())
            .as(StepVerifier::create)
            .expectNext(port)
            .expectComplete()
            .verify(Duration.ofMinutes(5));
    }


    //TODO: GET /networking/v1/external/policies?id=app-id-1,app-id-2
    @Test
    public void listFiltered() throws TimeoutException, InterruptedException {
        String destinationApplicationName = this.nameFactory.getApplicationName();
        String sourceApplicationName = this.nameFactory.getApplicationName();
        Integer port = this.nameFactory.getPort();

        this.spaceId
            .then(spaceId -> Mono.when(
                createApplicationId(this.cloudFoundryClient, destinationApplicationName, spaceId),
                createApplicationId(this.cloudFoundryClient, sourceApplicationName, spaceId)
            ))
            .then(function((destinationApplicationId, sourceApplicationId) -> requestCreatePolicy(this.networkingClient, destinationApplicationId, port, sourceApplicationId)
                .then(Mono.just(Tuples.of(destinationApplicationId, sourceApplicationId)))))
            .flatMapMany(function((destinationApplicationId, sourceApplicationId) -> this.networkingClient.policies()
                .list(ListPoliciesRequest.builder()
                    .policyGroupId(destinationApplicationId)// + "," + sourceApplicationId)
                    .build())
                .flatMapIterable(r -> {
                    System.out.println("***: " + destinationApplicationId + ", " + sourceApplicationId); //TODO: Remove!!!
                    System.out.println("***: " + r.getPolicies()); //TODO: Remove!!!
                    return r.getPolicies();
                })
                .map(policy -> policy.getDestination().getPorts().getStart())))
            .as(StepVerifier::create)
            .expectNext(port)
            .expectComplete()
            .verify(Duration.ofMinutes(5));
    }

    private static Mono<String> createApplicationId(CloudFoundryClient cloudFoundryClient, String applicationName, String spaceId) {
        return requestCreateApplication(cloudFoundryClient, spaceId, applicationName)
            .map(ResourceUtils::getId);
    }

    private static Mono<CreateApplicationResponse> requestCreateApplication(CloudFoundryClient cloudFoundryClient, String spaceId, String applicationName) {
        return cloudFoundryClient.applicationsV2()
            .create(CreateApplicationRequest.builder()
                .buildpack("staticfile_buildpack")
                .diego(true)
                .diskQuota(512)
                .memory(64)
                .name(applicationName)
                .spaceId(spaceId)
                .build());
    }

    private static Mono<Void> requestCreatePolicy(NetworkingClient networkingClient, String destinationApplicationId, Integer port, String sourceApplicationId) {
        return networkingClient.policies()
            .create(CreatePoliciesRequest.builder()
                .policy(Policy.builder()
                    .destination(Destination.builder()
                        .id(destinationApplicationId)
                        .ports(Ports.builder()
                            .end(port)
                            .start(port)
                            .build())
                        .protocol("tcp")
                        .build())
                    .source(Source.builder()
                        .id(sourceApplicationId)
                        .build())
                    .build())
                .build());
    }

    private static Mono<ListPoliciesResponse> requestListPolicies(NetworkingClient networkingClient) {
        return networkingClient.policies()
            .list(ListPoliciesRequest.builder()
                .build());
    }

}
