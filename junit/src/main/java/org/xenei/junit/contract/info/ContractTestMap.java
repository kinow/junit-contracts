package org.xenei.junit.contract.info;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xenei.junit.contract.ClassPathUtils;
import org.xenei.junit.contract.Contract;

/**
 * A map like object that maintains information about test classes and the
 * classes they test.
 *
 */
public class ContractTestMap {
	// the map of test classes to the TestInfo for it.
	private final Map<Class<?>, TestInfo> classToInfoMap = new HashMap<Class<?>, TestInfo>();
	// the map of interface under test to the TestInfo for it.
	private final Map<Class<?>, Set<TestInfo>> interfaceToInfoMap = new HashMap<Class<?>, Set<TestInfo>>();

	private final static Set<String> skipInterfaces;
	
	private static final Logger LOG = LoggerFactory
			.getLogger(ContractTestMap.class);

	static {
		skipInterfaces = new HashSet<String>();
		String prop = System.getProperty("contracts.skipClasses");
		if (prop != null)
		{
			for (String iFace : prop.split( "," ))
			{
				skipInterfaces.add(iFace);
			}
		}
	}
	/**
	 * Populate and return a ContractTestMap with all the contract tests on the
	 * class path.
	 *
	 * Will add to list of errors for tests that do not have proper annotations.
	 *
	 * @param errors
	 *            A list of errors.
	 * @return contractTestClasses TestInfo objects for classes annotated with @Contract
	 */
	public static ContractTestMap populateInstance() {

		final ContractTestMap retval = new ContractTestMap();
		// get all the classes that are Contract tests

		for (final Class<?> clazz : ClassPathUtils.getClasses("")) {
			if (! skipInterfaces.contains( clazz.getName() ))
			{
				// contract annotation is on the test class
				// value of contract annotation is class under test
				LOG.debug("seeking contracts for {}", clazz);
				final Contract c = clazz.getAnnotation(Contract.class);
				if (c != null) {
					LOG.debug("adding {} {}", clazz, c);
					retval.add(new TestInfo(clazz, c));
				}
			}
		}
		return retval;
	}

	/**
	 * Populate and return a ContractTestMap with all the contract tests on the
	 * classpath.
	 *
	 * Will add to list of errors for tests that do not have proper annotations.
	 *
	 * @param classLoader
	 *            The class loader to load classes from.
	 * @return contractTestClasses TestInfo objects for classes annotated with @Contract
	 */
	public static ContractTestMap populateInstance(final ClassLoader classLoader) {
		return populateInstance(classLoader, new String[] {
			""
		});
	}

	/**
	 *
	 * @param classLoader
	 *            The class loader to use.
	 * @param packages
	 *            A list of package names to report
	 * @return A ContractTestMap.
	 */
	public static ContractTestMap populateInstance(
			final ClassLoader classLoader, final String[] packages) {
		final ContractTestMap retval = new ContractTestMap();
		// get all the classes that are Contract tests

		for (final Class<?> clazz : ClassPathUtils.getClasses(classLoader, "")) {
			// contract annotation is on the test class
			// value of contract annotation is class under test

			boolean report = false;
			LOG.debug("Checking error logging for {}", clazz);
			for (final String pkg : packages) {
				LOG.debug("Checking {} against {}", pkg, clazz.getPackage());
				report |= clazz.getPackage().getName().startsWith(pkg);
			}
			if (report) {
				final Contract c = clazz.getAnnotation(Contract.class);
				if (c != null) {
					retval.add(new TestInfo(clazz, c));
				}
			}
		}

		return retval;
	}

	/**
	 * Add a TestInfo to the map.
	 *
	 * @param info
	 *            the info to add
	 */
	public void add(final TestInfo info) {
		classToInfoMap.put(info.getContractTestClass(), info);
		Set<TestInfo> tiSet = interfaceToInfoMap.get(info.getInterfaceClass());
		if (tiSet == null) {
			tiSet = new HashSet<TestInfo>();
			interfaceToInfoMap.put(info.getInterfaceClass(), tiSet);
		}
		tiSet.add(info);
	}

	/**
	 * Get a TestInfo for the test class.
	 *
	 * @param testClass
	 *            The test class.
	 * @return THe TestInfo for the test class.
	 */
	public TestInfo getInfoByTestClass(final Class<?> testClass) {
		return classToInfoMap.get(testClass);
	}

	/**
	 * Get a TestInfo for a interface under test.
	 *
	 * @param contract
	 *            The class (interface) under tes.t
	 * @return The TestInfo for the contract class.
	 */
	public Set<TestInfo> getInfoByInterfaceClass(final Class<?> contract) {
		return interfaceToInfoMap.get(contract);
	}

	/**
	 * Find the test classes for the specific contract class.
	 *
	 * @param contractClassInfo
	 *            A TestInfo object that represents the test class to search
	 *            for.
	 * @return the set of TestInfo objects that represent the complete suite of
	 *         contract tests for the contractClassInfo object.
	 */
	public Set<TestInfo> getAnnotatedClasses(final TestInfo contractClassInfo) {
		return getAnnotatedClasses(new LinkedHashSet<TestInfo>(),
				contractClassInfo);

	}

	/**
	 * Find the test classes for the specific contract class.
	 *
	 * Adds the results to the testClasses parameter set.
	 *
	 *
	 * @param testClasses
	 *            A set of testInfo to add the result to.
	 * @param contractClassInfo
	 *            A TestInfo object that represents the test class to search
	 *            for.
	 * @return the set of TestInfo objects that represent the complete suite of
	 *         contract tests for the contractClassInfo object.
	 */
	public Set<TestInfo> getAnnotatedClasses(final Set<TestInfo> testClasses,
			final TestInfo contractClassInfo) {

		// populate the set of implementation classes
		final Set<Class<?>> implClasses = ClassPathUtils
				.getAllInterfaces(contractClassInfo.getInterfaceClass());
		final List<Class<?>> skipList = Arrays.asList(contractClassInfo
				.getSkipTests());
		for (final Class<?> clazz : implClasses) {
			if (skipList.contains(clazz)) {
				LOG.info(String.format("Skipping %s for %s", clazz,
						contractClassInfo));
			}
			else {

				final Set<TestInfo> tiSet = getInfoByInterfaceClass(clazz);
				if (tiSet.isEmpty()) {
					LOG.info(String.format("Checked %s found nothing", clazz));
				}
				else {
					testClasses.addAll(tiSet);
				}

			}
		}
		return testClasses;
	}

	/**
	 * A list of all test Infos.
	 * 
	 * @return
	 */
	public Collection<TestInfo> listTestInfo() {
		return classToInfoMap.values();
	}
}