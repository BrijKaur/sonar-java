/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.bytecode.loader;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableRuleMigrationSupport
class SquidClassLoaderTest {

  @Rule
  public LogTester logTester = new LogTester();

  private SquidClassLoader classLoader;

  @AfterEach
  public void tearDown() {
    IOUtils.closeQuietly(classLoader);
  }

  /**
   * See SONAR-2824:
   * Created ClassLoader should be able to load classes only from JDK and from provided list of JAR-files,
   * thus it shouldn't be able to load his class.
   */
  @Test
  void shouldBeIsolated() throws Exception {
    classLoader = new SquidClassLoader(Collections.emptyList());
    assertThat(classLoader.loadClass("java.lang.Integer")).isNotNull();
    assertThat(classLoader.getResource("java/lang/Integer.class")).isNotNull();
    assertThrows(ClassNotFoundException.class,
      () -> classLoader.loadClass(SquidClassLoader.class.getName()));
  }

  @Test
  void should_read_child_classes_first() throws Exception {
    classLoader = new SquidClassLoader(Collections.singletonList(new File("src/test/files/bytecode/lib/likeJdkJar.jar")));
    URL resource = classLoader.getResource("java/lang/String.class");
    assertThat(resource).isNotNull();
    assertThat(resource.getFile()).contains("likeJdkJar.jar!");
  }
  @Test
  void createFromJar() throws Exception {
    File jar = new File("src/test/files/bytecode/lib/hello.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));

    assertThat(classLoader.loadClass("org.sonar.tests.Hello")).isNotNull();
    assertThat(classLoader.getResource("org/sonar/tests/Hello.class")).isNotNull();
    assertThat(Collections.list(classLoader.findResources("org/sonar/tests/Hello.class"))).hasSize(1);
    assertThrows(ClassNotFoundException.class,
      () -> classLoader.loadClass("foo.Unknown"));
  }

  @Test
  void createFromAar() throws Exception {
    File jar = new File("src/test/files/classpath/lib/oklog-1.0.1.aar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));

    assertThat(classLoader.loadClass("com.github.simonpercic.oklog.BuildConfig")).isNotNull();
    assertThat(classLoader.getResource("com/github/simonpercic/oklog/BuildConfig.class")).isNotNull();
    assertThat(Collections.list(classLoader.findResources("com/github/simonpercic/oklog/BuildConfig.class"))).hasSize(1);
    assertThrows(ClassNotFoundException.class,
      () -> classLoader.loadClass("foo.Unknown"));
  }

  @Test
  void unknownJarIsIgnored() throws Exception {
    File jar = new File("src/test/files/bytecode/lib/unknown.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));

    assertThat(classLoader.getResource("org/sonar/tests/Hello.class")).isNull();

    classLoader.close();
  }

  /**
   * SONAR-3693
   */
  @Test
  void not_jar_is_ignored() throws Exception {
    File jar = new File("src/test/files/bytecode/src/tags/TagName.java");
    classLoader = new SquidClassLoader(Arrays.asList(jar));
  }

  @Test
  void empty_archive_should_not_fail() throws Exception {
    File jar = new File("src/test/files/bytecode/lib/emptyArchive.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));

    assertThat(classLoader.getResource("dummy.class")).isNull();

    assertThat(excludeProgressReport(logTester.logs())).isEmpty();

    classLoader.close();
  }

  @Test
  void empty_file_should_not_fail_but_log_warning() {
    File jar = new File("src/test/files/bytecode/lib/emptyFile.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));

    assertThat(classLoader.getResource("dummy.class")).isNull();

    assertThat(excludeProgressReport(logTester.logs())).hasSize(2);
    List<String> warnings = logTester.logs(LoggerLevel.WARN);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0))
      .startsWith("Unable to load classes from '")
      .endsWith("emptyFile.jar\'");
    List<String> debugs = logTester.logs(LoggerLevel.DEBUG);
    assertThat(debugs).hasSize(1);
    assertThat(debugs.get(0))
      .startsWith("Unable to open")
      .endsWith("emptyFile.jar: zip file is empty");

    classLoader.close();
  }

  @Test
  void createFromDirectory() throws Exception {
    File dir = new File("src/test/files/bytecode/bin/");
    classLoader = new SquidClassLoader(Arrays.asList(dir));

    assertThat(classLoader.loadClass("tags.TagName")).isNotNull();
    assertThat(classLoader.getResource("tags/TagName.class")).isNotNull();
    assertThat(Collections.list(classLoader.findResources("tags/TagName.class"))).hasSize(1);
    assertThrows(ClassNotFoundException.class,
      () -> classLoader.loadClass("foo.Unknown"));
  }

  @Test
  void testFindResource() throws Exception {
    File dir = new File("src/test/files/bytecode/bin/");
    classLoader = new SquidClassLoader(Arrays.asList(dir, dir));
    assertThat(classLoader.findResource("tags/TagName.class")).isNotNull();
    assertThat(classLoader.findResource("notfound")).isNull();
  }

  @Test
  void testFindResources() throws Exception {
    File dir = new File("src/test/files/bytecode/bin/");
    classLoader = new SquidClassLoader(Arrays.asList(dir, dir));

    assertThat(Collections.list(classLoader.findResources("tags/TagName.class"))).hasSize(2);
    assertThat(Collections.list(classLoader.findResources("notfound"))).hasSize(0);
  }

  @Test
  void closeCanBeCalledMultipleTimes() throws Exception {
    File jar = new File("src/test/files/bytecode/lib/hello.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));
    classLoader.close();
    classLoader.close();
  }

  @Test
  void exceptionThrownWhenAlreadyClosed() {
    File jar = new File("src/test/files/bytecode/lib/hello.jar");
    classLoader = new SquidClassLoader(Arrays.asList(jar));
    classLoader.close();

    IllegalStateException e = assertThrows(IllegalStateException.class,
      () -> classLoader.getResource("org/sonar/tests/Hello.class"));
    assertThat(e.getMessage()).isEqualTo("java.lang.IllegalStateException: zip file closed");
  }

  @Test
  void test_loading_class() {
    SquidClassLoader classLoader = new SquidClassLoader(Collections.singletonList(new File("target/test-classes")));
    String className = getClass().getCanonicalName();
    byte[] bytes = classLoader.getBytesForClass(className);
    assertThat(bytes).isNotNull();
    ClassReader cr = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, 0);
    assertThat(classNode.name).isEqualTo("org/sonar/java/bytecode/loader/SquidClassLoaderTest");
  }

  @Test
  void empty_classloader_should_not_find_bytes() {
    SquidClassLoader classLoader = new SquidClassLoader(Collections.emptyList());
    String className = getClass().getCanonicalName();
    byte[] bytes = classLoader.getBytesForClass(className);
    assertThat(bytes).isNull();
  }

  @Test
  void test_loading_java9_class() throws Exception {
    SquidClassLoader classLoader = new SquidClassLoader(Collections.singletonList(new File("src/test/files/bytecode/java9/bin")));
    byte[] bytes = classLoader.getBytesForClass("org.test.Hello9");
    assertThat(bytes).isNotNull();
    ClassReader cr = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, 0);
    assertThat(classNode.version).isEqualTo(Opcodes.V9);
    classLoader.close();
  }

  @Test
  void test_loading_java10_class() throws Exception {
    SquidClassLoader classLoader = new SquidClassLoader(Collections.singletonList(new File("src/test/files/bytecode/java10/bin")));
    byte[] bytes = classLoader.getBytesForClass("org.foo.A");
    assertThat(bytes).isNotNull();
    ClassReader cr = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, 0);
    assertThat(classNode.version).isEqualTo(Opcodes.V10);
    classLoader.close();
  }

  @Test
  void test_loading_java11_class() throws Exception {
    SquidClassLoader classLoader = new SquidClassLoader(Collections.singletonList(new File("src/test/files/bytecode/java11/bin")));
    byte[] bytes = classLoader.getBytesForClass("org.foo.A");
    assertThat(bytes).isNotNull();
    ClassReader cr = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, 0);
    assertThat(classNode.version).isEqualTo(Opcodes.V11);
    classLoader.close();
  }

  /**
   * Some other tests do not wait termination of {@link org.sonarsource.analyzer.commons.ProgressReport} thread.
   */
  private static List<String> excludeProgressReport(List<String> logs) {
    return logs.stream()
      .filter(log -> !log.endsWith("source files have been analyzed"))
      .collect(Collectors.toList());
  }

}
