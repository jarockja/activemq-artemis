/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.integration.journal;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressSettingsInfo;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Before;
import org.junit.Test;

public class DuplicateRecordIdTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultInVMConfig().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(new SimpleString("dlq")).setExpiryAddress(new SimpleString("dlq"))));
      server.getConfiguration().setPersistenceEnabled(true);
   }

   @Test
   public void testDuplicateRecordId() throws Exception {
      for (int i = 0; i < 3; i++) {
         server.start();
         ActiveMQServerControl serverControl = server.getActiveMQServerControl();
         serverControl.removeAddressSettings("q");
         AddressSettingsInfo defaultSettings = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON("#"));
         AddressSettings settings = new AddressSettings();
         settings.setExpiryAddress(SimpleString.toSimpleString(defaultSettings.getExpiryAddress())).setExpiryDelay(defaultSettings.getExpiryDelay()).setMaxDeliveryAttempts(1)
                 .setMaxSizeBytes(defaultSettings.getMaxSizeBytes()).setPageSizeBytes(defaultSettings.getPageSizeBytes())
                 .setPageCacheMaxSize(defaultSettings.getPageCacheMaxSize()).setRedeliveryDelay(defaultSettings.getRedeliveryDelay())
                 .setMaxExpiryDelay(defaultSettings.getMaxRedeliveryDelay()).setRedistributionDelay(defaultSettings.getRedistributionDelay())
                 .setSendToDLAOnNoRoute(defaultSettings.isSendToDLAOnNoRoute()).setAddressFullMessagePolicy(AddressFullMessagePolicy.valueOf(defaultSettings.getAddressFullMessagePolicy()))
                 .setSlowConsumerThreshold(defaultSettings.getSlowConsumerThreshold())
                 .setSlowConsumerCheckPeriod(defaultSettings.getSlowConsumerCheckPeriod()).setSlowConsumerPolicy(SlowConsumerPolicy.valueOf(defaultSettings.getSlowConsumerPolicy()));
         serverControl.addAddressSettings("q", settings.toJSON());
         server.stop();
         PrintData.printData(server.getConfiguration().getBindingsLocation().getAbsoluteFile(), server.getConfiguration().getJournalLocation().getAbsoluteFile(), server.getConfiguration().getPagingLocation().getAbsoluteFile());
      }
   }
}
