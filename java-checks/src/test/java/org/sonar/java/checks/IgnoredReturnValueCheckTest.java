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
package org.sonar.java.checks;

import org.junit.jupiter.api.Test;
import org.sonar.java.checks.verifier.JavaCheckVerifier;

class IgnoredReturnValueCheckTest {

  @Test
  void test() {
    JavaCheckVerifier.newVerifier()
      .onFile("src/test/files/checks/IgnoredReturnValueCheck.java")
      .withCheck(new IgnoredReturnValueCheck())
      .verifyIssues();
    JavaCheckVerifier.newVerifier()
      .onFile("src/test/files/checks/IgnoredReturnValueCheckInternalCalls.java")
      .withCheck(new IgnoredReturnValueCheck())
      .verifyNoIssues();
  }

  @Test
  void java14_switch_expression() {
    JavaCheckVerifier.newVerifier()
      .onFile("src/test/files/checks/IgnoredReturnValueCheckJava14.java")
      .withJavaVersion(14)
      .withCheck(new IgnoredReturnValueCheck())
      .verifyIssues();
  }

}
