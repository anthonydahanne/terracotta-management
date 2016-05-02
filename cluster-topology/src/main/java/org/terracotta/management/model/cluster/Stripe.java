/*
 * Copyright Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.terracotta.management.model.cluster;

import org.terracotta.management.model.context.Context;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Mathieu Carbou
 */
public final class Stripe extends AbstractNode<Cluster> {

  public static final String KEY = "stripeId";

  private final ConcurrentMap<String, Server> servers = new ConcurrentHashMap<String, Server>();

  private Stripe(String name) {
    super(name);
  }

  public String getName() {
    return getId();
  }

  @Override
  String getContextKey() {
    return KEY;
  }

  public Map<String, Server> getServers() {
    return Collections.unmodifiableMap(servers);
  }

  public Stream<Server> serverStream() {
    return servers.values().stream();
  }

  public Cluster getCluster() {
    return getParent();
  }

  public int getServerCount() {
    return servers.size();
  }

  public Stripe addServer(Server server) {
    if (servers.putIfAbsent(server.getId(), server) != null) {
      throw new IllegalArgumentException("Duplicate server: " + server.getId());
    } else {
      server.setParent(this);
    }
    return this;
  }

  public Optional<Server> getServer(Context context) {
    return getServer(context.get(Server.KEY));
  }

  public Optional<Server> getServer(String id) {
    return id == null ? Optional.empty() : Optional.ofNullable(servers.get(id));
  }

  public Optional<Server> getServerByName(String serverName) {
    return serverStream().filter(server -> server.getServerName().equals(serverName)).findFirst();
  }

  public Optional<Server> getActiveServer() {
    return serverStream().filter(Server::isActive).findFirst();
  }

  public Optional<Server> removeServerByName(String serverName) {
    Optional<Server> server = getServerByName(serverName);
    server.ifPresent(s -> {
      if (servers.remove(s.getId(), s)) {
        s.detach();
      }
    });
    return server;
  }

  public Optional<Server> removeServer(String id) {
    Optional<Server> server = getServer(id);
    server.ifPresent(s -> {
      if (servers.remove(id, s)) {
        s.detach();
      }
    });
    return server;
  }

  public Optional<Manageable> getActiveManageable(Context context) {
    return getActiveServer().flatMap(s -> s.getManageable(context));
  }

  public Optional<Manageable> getActiveManageable(String name, String type) {
    return getActiveServer().flatMap(s -> s.getManageable(name, type));
  }

  public Stream<Manageable> manageableStream() {
    return serverStream().flatMap(NodeWithManageable::manageableStream);
  }

  public Stream<Manageable> activeManageableStream() {
    return getActiveServer().map(NodeWithManageable::manageableStream).orElse(Stream.empty());
  }

  @Override
  public void remove() {
    Cluster parent = getParent();
    if (parent != null) {
      parent.removeClient(getId());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    Stripe stripe = (Stripe) o;

    return servers.equals(stripe.servers);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + servers.hashCode();
    return result;
  }

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = super.toMap();
    map.put("name", getName());
    map.put("servers", serverStream().sorted((o1, o2) -> o1.getId().compareTo(o2.getId())).map(Server::toMap).collect(Collectors.toList()));
    return map;
  }

  public static Stripe create(String name) {
    return new Stripe(name);
  }

}