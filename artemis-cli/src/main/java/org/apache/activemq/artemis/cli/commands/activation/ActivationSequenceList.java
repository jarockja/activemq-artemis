/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.cli.commands.activation;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.LockAbstract;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedPrimitiveManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.quorum.DistributedLock;
import org.apache.activemq.artemis.quorum.DistributedPrimitiveManager;
import org.apache.activemq.artemis.quorum.MutableLong;

import static org.apache.activemq.artemis.cli.commands.activation.ActivationSequenceUtils.applyCoordinationId;

@Command(name = "list", description = "list local and/or coordinated activation sequences")
public class ActivationSequenceList extends LockAbstract {

   private static final int MANAGER_START_TIMEOUT_SECONDS = 60;
   @Option(name = "--node-id", description = "This can be used just with --remote option. If not set, broker NodeID is used instead")
   public String nodeId = null;
   @Option(name = "--remote", description = "List just coordinated activation sequence")
   public boolean remote = false;
   @Option(name = "--local", description = "List just local activation sequence")
   public boolean local = false;

   @Override
   public Object execute(ActionContext context) throws Exception {
      final Object output = super.execute(context);
      execute(this, getFileConfiguration(), context.out);
      return output;
   }

   public static final class ListResult {

      public final String nodeId;
      public final Long coordinatedActivationSequence;
      public final Long localActivationSequence;

      private ListResult(String nodeId, Long coordinatedActivationSequence, Long localActivationSequence) {
         this.nodeId = nodeId;
         this.coordinatedActivationSequence = coordinatedActivationSequence;
         this.localActivationSequence = localActivationSequence;
      }
   }

   /**
    * This has been exposed to ease testing it on integration tests: no need for brokerInstance
    */
   public static ListResult execute(final ActivationSequenceList command,
                                    final Configuration config,
                                    final PrintStream out) throws Exception {
      String nodeId = command.nodeId;
      final boolean remote = command.remote;
      final boolean local = command.local;
      if (remote && local) {
         throw new IllegalArgumentException("--local and --remote cannot be both present: to list both sequences just drop both options");
      }
      if (nodeId != null && !command.remote) {
         throw new IllegalArgumentException("--node-id must be used just with --remote");
      }
      final HAPolicyConfiguration policyConfig = config.getHAPolicyConfiguration();
      final DistributedPrimitiveManagerConfiguration managerConfiguration;
      String coordinationId = null;
      if (policyConfig instanceof ReplicationBackupPolicyConfiguration) {
         ReplicationBackupPolicyConfiguration backupPolicyConfig = (ReplicationBackupPolicyConfiguration) policyConfig;
         managerConfiguration = backupPolicyConfig.getDistributedManagerConfiguration();
      } else if (policyConfig instanceof ReplicationPrimaryPolicyConfiguration) {
         ReplicationPrimaryPolicyConfiguration primaryPolicyConfig = (ReplicationPrimaryPolicyConfiguration) policyConfig;
         managerConfiguration = primaryPolicyConfig.getDistributedManagerConfiguration();
         if (primaryPolicyConfig.getCoordinationId() != null) {
            coordinationId = primaryPolicyConfig.getCoordinationId();
         }
      } else {
         throw new UnsupportedOperationException("This command support just <primary> or <backup> replication configuration");
      }
      Objects.requireNonNull(managerConfiguration);
      NodeManager nodeManager = null;
      if (nodeId == null) {
         // check local activation sequence and Node ID
         nodeManager = new FileLockNodeManager(config.getNodeManagerLockLocation(), false);
         nodeManager.start();
      }
      try {
         if (nodeManager != null) {
            if (coordinationId != null) {
               if (!coordinationId.equals(nodeManager.getNodeId())) {
                  nodeManager = applyCoordinationId(coordinationId, nodeManager, config.getNodeManagerLockLocation());
               }
            }
         }
         Long localSequence = null;
         if (nodeManager != null) {
            assert nodeId == null;
            nodeId = nodeManager.getNodeId().toString();
         } else {
            assert nodeId != null;
         }
         if (!remote) {
            final long localActivationSequence = nodeManager.getNodeActivationSequence();
            if (localActivationSequence == NodeManager.NULL_NODE_ACTIVATION_SEQUENCE) {
               if (out != null) {
                  out.println("No local activation sequence for NodeID=" + nodeId);
               }
            } else {
               localSequence = localActivationSequence;
               if (out != null) {
                  out.println("Local activation sequence for NodeID=" + nodeId + ": " + localActivationSequence);
               }
            }
         }
         Long coordinatedSequence = null;
         if (!local) {
            try (DistributedPrimitiveManager manager = DistributedPrimitiveManager.newInstanceOf(
               managerConfiguration.getClassName(), managerConfiguration.getProperties())) {
               if (!manager.start(MANAGER_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("distributed manager isn't started in " + MANAGER_START_TIMEOUT_SECONDS + " seconds");

               }
               try (MutableLong coordinatedActivationSequence = manager.getMutableLong(nodeId);
                    DistributedLock liveLock = manager.getDistributedLock(nodeId)) {
                  if (!liveLock.tryLock()) {
                     throw new IllegalStateException("Cannot safely get the coordinated activation sequence for NodeID=" + nodeId + ": maybe the live lock is still held.");
                  }
                  coordinatedSequence = coordinatedActivationSequence.get();
                  if (out != null) {
                     out.println("Coordinated activation sequence for NodeID=" + nodeId + ": " + coordinatedSequence);
                  }
               }
            }
         }
         return new ListResult(nodeId, coordinatedSequence, localSequence);
      } finally {
         if (nodeManager != null) {
            nodeManager.stop();
         }
      }
   }

}