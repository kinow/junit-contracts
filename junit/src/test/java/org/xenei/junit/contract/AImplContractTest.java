/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xenei.junit.contract;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * Run the AT tests using the contract suite runner.
 * 
 * This will run the tests defined in AT as well as any other interface tests.
 * Compare this to AImplTest.
 * 
 * The use of the Listener interface in the before and after methods are to
 * track that the tests are run correctly and in the proper order. This would
 * not be used in a production test but are part of our testing of
 * junit-contracts.
 * 
 */
// run with contract suite
@RunWith(ContractSuite.class)
// testing AImpl
@ContractImpl(AImpl.class)
public class AImplContractTest {

	// create the producer to inject
	private IProducer<AImpl> producer = new IProducer<AImpl>() {

		@Override
		public AImpl newInstance() {
			Listener.add("AImplContractTest.producer.newInstance()");
			return new AImpl();
		}

		@Override
		public void cleanUp() {
			Listener.add("AImplContractTest.producer.cleanUp()");
		}

	};

	public AImplContractTest() {
	}

	/**
	 * The method to get the producer to inject it into tests
	 * 
	 * @return
	 */
	@Contract.Inject
	public IProducer<AImpl> getProducer() {
		return producer;
	}

	@BeforeClass
	public static void beforeClass() {
		Listener.clear();
	}

	@AfterClass
	public static void afterClass() {
		String[] expected = { "AImplContractTest.producer.newInstance()",
				"aname", "AImplContractTest.producer.cleanUp()" };

		List<String> l = Listener.get();
		Assert.assertEquals(Arrays.asList(expected), l);

	}
}
