package org.xenei.contracts.maven;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.xenei.junit.contract.ClassPathUtils;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractImpl;
import org.xenei.junit.contract.NoContractTest;
import org.xenei.junit.contract.tooling.InterfaceInfo;
import org.xenei.junit.contract.tooling.InterfaceReport;

@Mojo(name = "contract-test", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, requiresDependencyResolution = ResolutionScope.TEST)
public class ContractMojo extends AbstractMojo {

	@Parameter
	private String[] packages;

	@Parameter
	private ReportConfig untested;

	@Parameter
	private ReportConfig unimplemented;

	@Parameter
	private ReportConfig errors;

	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private File classDir;

	@Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
	private File testDir;

	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	private File target;

	@Component
	private MavenProject project;

	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;
	@Component
	private RepositorySystem repositorySystem;
	@Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
	private ArtifactRepository localRepository;

	private Set<Artifact> junitContractsArtifacts;

	private File myDir;

	private final StringBuilder failureMessage = new StringBuilder();

	public ContractMojo() {
	}

	public void setPackages(final String[] packages) {
		this.packages = packages;
	}

	public void setErrors(final ReportConfig errors) {
		this.errors = errors;
	}
	
	public void setUntested(ReportConfig untested) {
		this.untested = untested;
	}

	public void setUnimplemented(ReportConfig unimplemented) {
		this.unimplemented = unimplemented;
	}

	@Override
	public void execute() throws MojoExecutionException {
		boolean success = true;

		if ((packages == null) || (packages.length == 0)) {
			getLog().error("At least one package must be specified");
			throw new MojoExecutionException(
					"At least one package must be specified");
		}

		if (getLog().isDebugEnabled()) {
			getLog().debug("PACKAGES: ");
			for (final String s : packages) {
				getLog().debug("PKG: " + s);
			}
		}

		myDir = new File(target, "contract-reports");
		if (!myDir.exists()) {
			myDir.mkdirs();
		}

		InterfaceReport ir;
		try {
			ir = new InterfaceReport(packages, null, buildClassLoader());
		} catch (final MalformedURLException e1) {
			throw new MojoExecutionException(
					"Could not create Interface report class", e1);
		}

		doReportInterfaces(ir);

		success &= doReportUntested(ir.getUntestedInterfaces());

		success &= doReportUnimplemented(ir.getUnImplementedTests());

		success &= doReportErrors(ir.getErrors());

		if (!success) {
			throw new MojoExecutionException(failureMessage.toString());
		}
	}

	private void addFailureMessage(final String msg) {
		addFailureMessage(msg, null);
	}

	private void addFailureMessage(final String msg, final Exception e) {
		if (failureMessage.length() > 0) {
			failureMessage.append(System.getProperty("line.separator"));
		}
		if (e == null) {
			getLog().warn(msg);
		}
		else {
			getLog().warn(msg, e);
		}
		failureMessage.append(msg);
	}

	private void doReportInterfaces(final InterfaceReport ir) {
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(new File(myDir,
					"interfaces.txt")));
			for (final InterfaceInfo ii : ir.getInterfaceInfoCollection()) {
				final String entry = String.format("Interface: %s %s", ii
						.getName().getName(), ii.getTests());
				if (getLog().isDebugEnabled()) {
					getLog().debug(entry);
				}
				bw.write(entry);
				bw.newLine();
			}

			for (final Class<?> cls : ir.getPackageClasses()) {
				final String entry = String.format(
						"Class: %s, contract: %s, impl: %s, flg: %s, all: %s",
						cls.getName(),
						cls.getAnnotation(Contract.class) != null,
						cls.getAnnotation(ContractImpl.class) != null,
						cls.getAnnotation(NoContractTest.class) != null,
						Arrays.asList(cls.getAnnotations()));
				if (getLog().isDebugEnabled()) {
					getLog().debug(entry);
				}
				bw.write(entry);
				bw.newLine();
			}

		} catch (final IOException e) {
			getLog().warn(e.getMessage(), e);
		} finally {
			IOUtils.closeQuietly(bw);
		}
	}

	private boolean doReportUntested(final Set<Class<?>> untestedInterfaces) {

		if (!untestedInterfaces.isEmpty()) {
			if (untested.isReporting()) {

				BufferedWriter bw = null;
				try {
					bw = new BufferedWriter(new FileWriter(new File(myDir,
							"untested.txt")));
					for (final Class<?> c : untestedInterfaces) {
						bw.write(c.getName());
						bw.newLine();
					}
				} catch (final IOException e) {
					addFailureMessage("Unable to write untested report", e);
					return false;
				} finally {
					IOUtils.closeQuietly(bw);
				}
			}
			if (untested.isFailOnError()) {
				addFailureMessage("Untested Interfaces Exist");
				return false;
			}
		}
		return true;
	}

	private boolean doReportUnimplemented(final Set<Class<?>> unimplementedTests) {

		if (!unimplementedTests.isEmpty()) {
			if (unimplemented.isReporting()) {
				BufferedWriter bw = null;
				try {
					bw = new BufferedWriter(new FileWriter(new File(myDir,
							"unimplemented.txt")));
					for (final Class<?> c : unimplementedTests) {
						bw.write(c.getName());
						bw.newLine();
					}
				} catch (final IOException e) {
					addFailureMessage("Unable to write unimplemented report", e);
					return false;
				} finally {
					IOUtils.closeQuietly(bw);
				}
			}
			if (unimplemented.isFailOnError()) {
				addFailureMessage("Unimplemented Tests Exist");
				return false;
			}
		}
		return true;
	}

	private boolean doReportErrors(final List<Throwable> errorLst) {
		if (!errorLst.isEmpty()) {
			if (errors.isReporting()) {
				BufferedWriter bw = null;
				try {
					bw = new BufferedWriter(new FileWriter(new File(myDir,
							"errors.txt")));
					for (final Throwable t : errorLst) {
						bw.write(t.toString());
						bw.newLine();
					}
				} catch (final IOException e) {
					addFailureMessage("Unable to write error report", e);
					return false;
				} finally {
					IOUtils.closeQuietly(bw);
				}
			}
			if (errors.isFailOnError()) {
				addFailureMessage("Contract Test Errors Exist");
				return false;
			}
		}
		return true;
	}

	private ClassLoader buildClassLoader() throws MojoExecutionException {
		final ClassWorld world = new ClassWorld();
		ClassRealm realm;

		try {
			realm = world.newRealm("contract", null);

			// add contract test and it's transient dependencies.
			for (final Artifact elt : getJunitContractsArtifacts()) {
				final String dir = String.format("%s!/", elt.getFile().toURI()
						.toURL());
				if (getLog().isDebugEnabled()) {
					getLog().debug("Checking for imports from: " + dir);
				}
				try {
					final Set<String> classNames = ClassPathUtils.findClasses(
							dir, "org.xenei.junit.contract");
					for (final String clsName : classNames) {
						if (getLog().isDebugEnabled()) {
							getLog().debug(
									"Importing from current classloader: "
											+ clsName);
						}
						importFromCurrentClassLoader(realm,
								Class.forName(clsName));
					}
				} catch (final ClassNotFoundException e) {
					throw new MojoExecutionException(e.toString(), e);
				} catch (final IOException e) {
					throw new MojoExecutionException(e.toString(), e);
				}
			}

			// add source dirs
			for (final String elt : project.getCompileSourceRoots()) {
				final URL url = new File(elt).toURI().toURL();
				realm.addURL(url);
				if (getLog().isDebugEnabled()) {
					getLog().debug("Source root: " + url);
				}
			}

			// add Compile classpath
			for (final String elt : project.getCompileClasspathElements()) {
				final URL url = new File(elt).toURI().toURL();
				realm.addURL(url);
				if (getLog().isDebugEnabled()) {
					getLog().debug("Compile classpath: " + url);
				}
			}

			// add Test classpath
			for (final String elt : project.getTestClasspathElements()) {
				final URL url = new File(elt).toURI().toURL();
				realm.addURL(url);
				if (getLog().isDebugEnabled()) {
					getLog().debug("Test classpath: " + url);
				}
			}
		} catch (final DuplicateRealmException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		} catch (final MalformedURLException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		} catch (final DependencyResolutionRequiredException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
		return realm;
	}

	private void importFromCurrentClassLoader(final ClassRealm realm,
			final Class<?> cls) {
		if (cls == null) {
			return;
		}
		realm.importFrom(Thread.currentThread().getContextClassLoader(),
				cls.getName());
		// ClassRealm importing is prefix-based, so no need to specifically add
		// inner classes
		for (final Class<?> intf : cls.getInterfaces()) {
			importFromCurrentClassLoader(realm, intf);
		}
		importFromCurrentClassLoader(realm, cls.getSuperclass());
	}

	private Set<Artifact> getJunitContractsArtifacts() {
		if (junitContractsArtifacts == null) {
			final ArtifactResolutionRequest request = new ArtifactResolutionRequest()
			.setArtifact(
					pluginArtifactMap.get("org.xenei:junit-contracts"))
					.setResolveTransitively(true)
					.setLocalRepository(localRepository);
			final ArtifactResolutionResult result = repositorySystem
					.resolve(request);
			junitContractsArtifacts = result.getArtifacts();
		}
		return junitContractsArtifacts;
	}
}
