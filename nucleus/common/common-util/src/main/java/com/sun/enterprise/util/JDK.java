/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
// Portions Copyright [2018-2019] [Payara Foundation and/or its affiliates]

package com.sun.enterprise.util;

/**
 * A simple class that fills a hole in the JDK.  It parses out the version numbers
 *  of the JDK we are running.
 * Example:<p>
 * 1.6.0_u14 == major = 1 minor = 6, subminor = 0, update = 14
 *
 * @author bnevins
 */
public final class JDK {
    /**
     * See if the current JDK is legal for running GlassFish
     * @return true if the JDK is >= 1.6.0
     */
    public static boolean ok() {
        return major == 1 && minor >= 6;
    }

    public static int getMajor() {
        return major;
    }
    public static int getMinor() {
        return minor;
    }

    public static int getSubMinor() {
        return subminor;
    }

    public static int getUpdate() {
        return update;
    }
    
    public static String getVendor() {
        return vendor;
    }

    public static class Version {
        private final String vendor;
        private final int major;
        private final Integer minor;
        private final Integer subminor;
        private final Integer update;

        private Version(String version) {
            // split java version into it's constituent parts, i.e.
            // 1.2.3.4 -> [ 1, 2, 3, 4]
            // 1.2.3u4 -> [ 1, 2, 3, 4]
            // 1.2.3_4 -> [ 1, 2, 3, 4]
            
            if (version.contains("-")) {
                String[] versionSplit = version.split("-");
                vendor = versionSplit.length > 0 ? versionSplit[0] : null;
                version = versionSplit.length > 1 ? versionSplit[1] : "";
            } else {
                vendor = null;
            }
            
            String[] split = version.split("[\\._u\\-]+");

            major = split.length > 0 ? Integer.parseInt(split[0]) : 0;
            minor = split.length > 1 ? Integer.parseInt(split[1]) : null;
            subminor = split.length > 2 ? Integer.parseInt(split[2]) : null;
            update = split.length > 3 ? Integer.parseInt(split[3]) : null;
        }

        private Version() {
            vendor = JDK.vendor;
            major = JDK.major;
            minor = JDK.minor;
            subminor = JDK.subminor;
            update = JDK.update;
        }

        public boolean newerThan(Version version) {
            if (major > version.major) {
                return true;
            } else if (major == version.major) {
                if (greaterThan(minor, version.minor)) {
                    return true;
                } else if (equals(minor, version.minor)) {
                    if (greaterThan(subminor, version.subminor)) {
                        return true;
                    } else if (equals(subminor, version.subminor)) {
                        if (greaterThan(update, version.update)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

       public boolean olderThan(Version version) {
            if (major < version.major) {
                return true;
            } else if (major == version.major) {
                if (lessThan(minor, version.minor)) {
                    return true;
                } else if (equals(minor, version.minor)) {
                    if (lessThan(subminor, version.subminor)) {
                        return true;
                    } else if (equals(subminor, version.subminor)) {
                        if (lessThan(update, version.update)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

        private static boolean greaterThan(Integer leftHandSide, Integer rightHandSide) {
            return (leftHandSide == null ? 0 : leftHandSide) > (rightHandSide == null ? 0 : rightHandSide);
        }

        private static boolean lessThan(Integer leftHandSide, Integer rightHandSide) {
            return (leftHandSide == null ? 0 : leftHandSide) < (rightHandSide == null ? 0 : rightHandSide);
        }

        /**
         * if either left-hand-side or right-hand-side is empty, it is equals
         *
         * @param leftHandSide
         * @param rightHandSide
         * @return true if equals, otherwise false
         */
        private static boolean equals(Integer leftHandSide, Integer rightHandSide) {

            if (leftHandSide == null || rightHandSide == null) {
                return true;
            }
            return leftHandSide.equals(rightHandSide);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 61 * hash + this.major;
            hash = 61 * hash + (this.minor == null ? 0 : this.minor);
            hash = 61 * hash + (this.subminor == null ? 0 : this.subminor);
            hash = 61 * hash + (this.update == null ? 0 : this.update);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Version other = (Version) obj;
            if (this.major != other.major) {
                return false;
            }
            if (!equals(this.minor, other.minor)) {
                return false;
            }
            if (!equals(this.subminor, other.subminor)) {
                return false;
            }
            if (!equals(this.update, other.update)) {
                return false;
            }
            return true;
        }

        public boolean newerOrEquals(Version version) {
            return newerThan(version) || equals(version);
        }

        public boolean olderOrEquals(Version version) {
            return olderThan(version) || equals(version);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(10);
            sb.append(major);
            if (minor != null) {
                sb.append('.').append(minor);
            }
            if (subminor != null) {
                sb.append('.').append(subminor);
            }
            if (update != null) {
                sb.append('.').append(update);
            }
            return sb.toString();
        }
    }

    public static Version getVersion(String string) {
        if (string != null && string.matches("([0-9]+[\\._u\\-]+)*[0-9]+")) {
            // make sure the string is a valid JDK version, i.e.
            // 1.8.0_162 or something that is returned by "java -version"
            return new Version(string);
        } else {
            return null;
        }
    }

    public static Version getVersion() {
        return new Version();
    }

    public static boolean isCorrectJDK(Version minVersion, Version maxVersion) {
        return isCorrectJDK(JDK_VERSION, minVersion, maxVersion);
    }
    
    public static boolean isCorrectJDK(Version reference, Version minVersion, Version maxVersion) {
        return isCorrectJDK(reference, null, minVersion, maxVersion);
    }

    /**
     * Check if the reference version falls between the minVersion and maxVersion.
     *
     * @param reference The version to compare; falls back to the current JDK version if empty.
     * @param minVersion The inclusive minimum version.
     * @param maxVersion The inclusive maximum version.
     * @return true if within the version range, false otherwise
     */
    public static boolean isCorrectJDK(Version reference, String vendor, Version minVersion, Version maxVersion) {
        Version version = reference == null ? JDK_VERSION : reference;
        boolean correctJDK = true;
      
        if (reference == null) {
            version = JDK_VERSION;
        }

        if (vendor != null) {
            correctJDK = JDK.vendor.contains(vendor);
        }

        if (correctJDK && minVersion != null) {
            correctJDK = version.newerOrEquals(minVersion);
        }
        
        if (correctJDK && maxVersion != null) {
            correctJDK = version.olderOrEquals(maxVersion);
        }
        
        return correctJDK;
    }

    /**
     * No instances are allowed so it is pointless to override toString
     * @return Parsed version numbers
     */
    public static String toStringStatic() {
        return "major: " + JDK.getMajor() +
        "\nminor: " + JDK.getMinor() +
        "\nsubminor: " + JDK.getSubMinor() +
        "\nupdate: " + JDK.getUpdate() +
        "\nOK ==>" + JDK.ok();
    }

    static {
        initialize();
    }

    // DO NOT initialize these variables.  You'll be sorry if you do!
    private static int major;
    private static int minor;
    private static int subminor;
    private static int update;
    private static String vendor;
    private static Version JDK_VERSION;

    // silently fall back to ridiculous defaults if something is crazily wrong...
    private static void initialize() {
        major = 1;
        minor = subminor = update = 0;
        try {
            String javaVersion = System.getProperty("java.version");
            vendor = System.getProperty("java.vendor");
       
            /*In JEP 223 java.specification.version will be a single number versioning , not a dotted versioning . So if we get a single
            integer as versioning we know that the JDK is post JEP 223
            For JDK 8:
                java.specification.version  1.8
                java.version    1.8.0_122
             For JDK 9:
                java.specification.version 9
                java.version 9.1.2
            */
            String javaSpecificationVersion = System.getProperty("java.specification.version");
            String[] jsvSplit = javaSpecificationVersion.split("\\.");
            if (jsvSplit.length == 1) {
                //This is handle Early Access build .Example 9-ea
                String[] jvSplit = javaVersion.split("-");
                String jvReal = jvSplit[0];
                String[] split = jvReal.split("[\\.]+");

                if (split.length > 0) {
                    if (split.length > 0) {
                        major = Integer.parseInt(split[0]);
                    }
                    if (split.length > 1) {
                        minor = Integer.parseInt(split[1]);
                    }
                    if (split.length > 2) {
                        subminor = Integer.parseInt(split[2]);
                    }
                    if (split.length > 3) {
                        update = Integer.parseInt(split[3]);
                    }
                }
            } else {
                if (!StringUtils.ok(javaVersion))
                    return; // not likely!!

                String[] ss = javaVersion.split("\\.");

                if (ss.length < 3 || !ss[0].equals("1"))
                    return;

                major = Integer.parseInt(ss[0]);
                minor = Integer.parseInt(ss[1]);
                ss = ss[2].split("_");

                if (ss.length < 1)
                    return;

                subminor = Integer.parseInt(ss[0]);

                if (ss.length > 1)
                    update = Integer.parseInt(ss[1]);
            }
        }
        catch(Exception e) {
            // ignore -- use defaults
        }

        JDK_VERSION = new Version();
    }
}