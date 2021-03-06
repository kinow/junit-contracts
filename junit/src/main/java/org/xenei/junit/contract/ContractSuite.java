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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xenei.junit.contract.info.ContractTestMap;
import org.xenei.junit.contract.info.DynamicSuiteInfo;
import org.xenei.junit.contract.info.DynamicTestInfo;
import org.xenei.junit.contract.info.SuiteInfo;
import org.xenei.junit.contract.info.TestInfo;
import org.xenei.junit.contract.info.TestInfoErrorRunner;

/**
 * Class that runs the Contract annotated tests.
 *
 * Used with <code>@RunWith( ContractSuite.class )</code> this class scans the
 * classes on the class path to find all the test implementations that should be
 * run by this test suite.
 * <p>
 * Tests annotated with <code>@RunWith( ContractSuite.class )</code> must:
 * <ol>
 * <li>Have a <code>ContractImpl</code> annotation specifying the implementation
 * being tested</li>
 * <li>Include a <code>@Contract.Inject</code> annotated getter that returns an
 * IProducer<x> where "x" is the class specified in the ContractImpl</li>
 * </ol>
 * <p>
 * The ContractSuite will:
 * <ol>
 * <li>Instantiate the class annotated with
 * <code>@RunWith( ContractSuite.class )</code></li>
 * <li>Find all the Contract tests for the class specified by ContractImpl and
 * add them to the test suite</li>
 * <li>execute all of the @ContractTest annotated tests</li>
 * </ol>
 * </p>
 * <p>
 * <b>NOTE:</b>If the class annotated with
 * <code>@RunWith( ContractSuite.class )</code> implements Dynamic the above
 * requirements change. See Dynamic for more information.
 * </p>
 */
@Ignore("Not a real test")
public class ContractSuite extends ParentRunner<Runner> {
	private static final Logger LOG = LoggerFactory
			.getLogger(ContractSuite.class);
	private final List<Runner> fRunners;

	/**
	 * Called reflectively on classes annotated with
	 * <code>@RunWith(Suite.class)</code>
	 *
	 * @param cls
	 *            the root class
	 * @param builder
	 *            builds runners for classes in the suite
	 * @throws Throwable
	 */
	public ContractSuite(final Class<?> cls, final RunnerBuilder builder)
			throws Throwable {
		super(cls);

		// List<Throwable> errors = new ArrayList<Throwable>();
		// find all the contract annotated tests on the class path.
		final ContractTestMap contractTestMap = ContractTestMap.populateInstance();

		final Object baseObj = cls.newInstance();
		List<Runner> r;
		if (baseObj instanceof Dynamic) {
			r = addDynamicClasses(builder, contractTestMap, (Dynamic) baseObj);
		}
		else {
			r = addAnnotatedClasses(cls, builder, contractTestMap, baseObj);
		}

		fRunners = Collections.unmodifiableList(r);
	}

	/**
	 * Get the ContractImpl annotation. Logs an error if the annotation is not
	 * found.
	 *
	 * @param cls
	 *            The class to look on
	 * @param errors
	 *            The list of errors to add to if there is an error
	 * @return ContractImpl or null if not found.
	 * @throws InitializationError
	 */
	private ContractImpl getContractImpl(final Class<?> cls)
			throws InitializationError {
		final ContractImpl impl = cls.getAnnotation(ContractImpl.class);
		if (impl == null) {
			throw new InitializationError(
					"Classes annotated as @RunWith( ContractSuite ) [" + cls
					+ "] must also be annotated with @ContractImpl");
		}
		return impl;
	}

	/**
	 * Add dynamic classes to the suite.
	 *
	 * @param builder
	 *            The builder to use
	 * @param errors
	 *            The list of errors
	 * @param contractTestMap
	 *            The ContractTest map.
	 * @param dynamic
	 *            The instance of the dynamic test.
	 * @return The list of runners.
	 * @throws InitializationError
	 */
	private List<Runner> addDynamicClasses(final RunnerBuilder builder,
			final ContractTestMap contractTestMap, final Dynamic dynamic)
			throws InitializationError {
		final Class<? extends Dynamic> cls = dynamic.getClass();
		// this is the list of all the JUnit runners in the suite.
		final List<Runner> r = new ArrayList<Runner>();
		ContractImpl impl = getContractImpl(cls);
		if (impl == null) {
			return r;
		}
		final DynamicSuiteInfo dynamicSuiteInfo = new DynamicSuiteInfo(cls,
				impl);

		final Collection<Class<?>> tests = dynamic.getSuiteClasses();
		if ((tests == null) || (tests.size() == 0)) {
			throw new InitializationError(
					"Dynamic suite did not return a list of classes to execute");
		}
		else {
			for (final Class<?> test : tests) {
				final RunWith runwith = test.getAnnotation(RunWith.class);
				if ((runwith != null)
						&& runwith.value().equals(ContractSuite.class)) {
					impl = getContractImpl(test);
					if (impl != null) {
						final DynamicTestInfo parentTestInfo = new DynamicTestInfo(
								test, impl, dynamicSuiteInfo);
						addSpecifiedClasses(r, test, builder, contractTestMap,
								dynamic, parentTestInfo);
					}
				}
				else {
					try {
						r.add(builder.runnerForClass(test));
					} catch (final Throwable t) {
						throw new InitializationError(t);
					}
				}
			}
		}
		return r;

	}

	/**
	 * Add annotated classes to the test
	 *
	 * @param cls
	 *            the base test class
	 * @param builder
	 *            The builder to use
	 * @param errors
	 *            the list of errors
	 * @param contractTestMap
	 *            The ContractTest map.
	 * @param baseObj
	 *            this is the instance object that we will use to get the
	 *            producer instance.
	 * @return the list of runners
	 * @throws InitializationError
	 */
	private List<Runner> addAnnotatedClasses(final Class<?> cls,
			final RunnerBuilder builder, final ContractTestMap contractTestMap,
			final Object baseObj) throws InitializationError {
		final List<Runner> r = new ArrayList<Runner>();
		final ContractImpl impl = getContractImpl(cls);
		if (impl != null) {
			TestInfo testInfo = contractTestMap
					.getInfoByTestClass(impl.value());
			if (testInfo == null) {
				testInfo = new SuiteInfo(cls, impl);
				contractTestMap.add(testInfo);
			}
			addSpecifiedClasses(r, cls, builder, contractTestMap, baseObj,
					testInfo);
		}
		return r;
	}

	/**
	 * Adds the specified classes to to the test suite.
	 *
	 * @param r
	 *            The list of runners to add the test to
	 * @param cls
	 *            The class under test
	 * @param builder
	 *            The builder to user
	 * @param errors
	 *            The list of errors.
	 * @param contractTestMap
	 *            The ContractTestMap
	 * @param baseObj
	 * @param parentTestInfo
	 * @throws InitializationError
	 */
	private void addSpecifiedClasses(final List<Runner> r, final Class<?> cls,
			final RunnerBuilder builder, final ContractTestMap contractTestMap,
			final Object baseObj, final TestInfo parentTestInfo)
			throws InitializationError {
		// this is the list of all the JUnit runners in the suite.

		final Set<TestInfo> testClasses = new LinkedHashSet<TestInfo>();
		// we have a RunWith annotated class: Klass
		// see if it is in the annotatedClasses

		final BaseClassRunner bcr = new BaseClassRunner(cls);
		if (bcr.computeTestMethods().size() > 0) {
			r.add(bcr);
		}

		// get all the annotated classes that test interfaces that
		// parentTestInfo
		// implements and iterate over them
		for (final TestInfo testInfo : contractTestMap.getAnnotatedClasses(
				testClasses, parentTestInfo)) {

			if (testInfo.getErrors().size() > 0) {
				LOG.error("Errors during parsing "+ testInfo);
				final TestInfoErrorRunner runner = new TestInfoErrorRunner(cls,testInfo);
						
				runner.logErrors( LOG );
				r.add(runner);
			}
			else {
				r.add(new ContractTestRunner(baseObj, parentTestInfo, testInfo));
			}
		}
		if (r.size() == 0) {
			throw new InitializationError("No tests for " + cls);
		}

	}

	@Override
	protected List<Runner> getChildren() {
		return fRunners;
	}

	@Override
	protected Description describeChild(final Runner child) {
		return child.getDescription();
	}

	@Override
	protected void runChild(final Runner child, final RunNotifier notifier) {
		child.run(notifier);
	}

	/**
	 * Class to run tests added to the base test.
	 *
	 */
	private class BaseClassRunner extends BlockJUnit4ClassRunner {

		private List<FrameworkMethod> testMethods = null;

		public BaseClassRunner(final Class<?> cls) throws InitializationError {
			super(cls);
		}

		@Override
		protected Statement withAfterClasses(final Statement statement) {
			return statement;
		}

		@Override
		protected Statement withBeforeClasses(final Statement statement) {
			return statement;
		}

		@Override
		protected void validateInstanceMethods(final List<Throwable> errors) {
			validatePublicVoidNoArgMethods(After.class, false, errors);
			validatePublicVoidNoArgMethods(Before.class, false, errors);
			validateTestMethods(errors);
		}

		@Override
		protected List<FrameworkMethod> computeTestMethods() {
			if (testMethods == null) {
				testMethods = new ArrayList<FrameworkMethod>();
				for (final FrameworkMethod mthd : super.getTestClass()
						.getAnnotatedMethods(ContractTest.class)) {
					if (mthd.getMethod().getDeclaringClass()
							.getAnnotation(Contract.class) == null) {
						testMethods.add(mthd);
					}
				}
			}
			return testMethods;
		}
	}
}
