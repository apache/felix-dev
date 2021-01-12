/*
 * Copyright (c) OSGi Alliance (2005, 2020). All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.framework;

import static java.util.Objects.requireNonNull;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.osgi.framework.connect.FrameworkUtilHelper;

/**
 * Framework Utility class.
 * 
 * <p>
 * This class contains utility methods which access Framework functions that may
 * be useful to bundles.
 * 
 * @since 1.3
 * @ThreadSafe
 * @author $Id: 71423feb17277b685e6d5d7864907e768da8cd0b $
 */
public class FrameworkUtil {
	/**
	 * FrameworkUtil objects may not be constructed.
	 */
	private FrameworkUtil() {
		// private empty constructor to prevent construction
	}

	/**
	 * Creates a {@code Filter} object. This {@code Filter} object may be used
	 * to match a {@code ServiceReference} object or a {@code Dictionary}
	 * object.
	 * 
	 * <p>
	 * If the filter cannot be parsed, an {@link InvalidSyntaxException} will be
	 * thrown with a human readable message where the filter became unparsable.
	 * 
	 * <p>
	 * This method returns a Filter implementation which may not perform as well
	 * as the framework implementation-specific Filter implementation returned
	 * by {@link BundleContext#createFilter(String)}.
	 * 
	 * @param filter The filter string.
	 * @return A {@code Filter} object encapsulating the filter string.
	 * @throws InvalidSyntaxException If {@code filter} contains an invalid
	 *         filter string that cannot be parsed.
	 * @throws NullPointerException If {@code filter} is null.
	 * 
	 * @see Filter
	 */
	public static Filter createFilter(String filter) throws InvalidSyntaxException {
		return FilterImpl.createFilter(filter);
	}

	/**
	 * Match a Distinguished Name (DN) chain against a pattern. DNs can be
	 * matched using wildcards. A wildcard ({@code '*'} &#92;u002A) replaces all
	 * possible values. Due to the structure of the DN, the comparison is more
	 * complicated than string-based wildcard matching.
	 * <p>
	 * A wildcard can stand for zero or more DNs in a chain, a number of
	 * relative distinguished names (RDNs) within a DN, or the value of a single
	 * RDN. The DNs in the chain and the matching pattern are canonicalized
	 * before processing. This means, among other things, that spaces must be
	 * ignored, except in values.
	 * <p>
	 * The format of a wildcard match pattern is:
	 * 
	 * <pre>
	 * matchPattern ::= dn-match ( ';' dn-match ) *
	 * dn-match     ::= ( '*' | rdn-match ) ( ',' rdn-match ) * | '-'
	 * rdn-match    ::= name '=' value-match
	 * value-match  ::= '*' | value-star
	 * value-star   ::= &lt; value, requires escaped '*' and '-' &gt;
	 * </pre>
	 * <p>
	 * The most simple case is a single wildcard; it must match any DN. A
	 * wildcard can also replace the first list of RDNs of a DN. The first RDNs
	 * are the least significant. Such lists of matched RDNs can be empty.
	 * <p>
	 * For example, a match pattern with a wildcard that matches all DNs that
	 * end with RDNs of o=ACME and c=US would look like this:
	 * 
	 * <pre>
	 * *, o=ACME, c=US
	 * </pre>
	 * 
	 * This match pattern would match the following DNs:
	 * 
	 * <pre>
	 * cn = Bugs Bunny, o = ACME, c = US
	 * ou = Carrots, cn=Daffy Duck, o=ACME, c=US
	 * street = 9C\, Avenue St. Drézéry, o=ACME, c=US
	 * dc=www, dc=acme, dc=com, o=ACME, c=US
	 * o=ACME, c=US
	 * </pre>
	 * 
	 * The following DNs would not match:
	 * 
	 * <pre>
	 * street = 9C\, Avenue St. Drézéry, o=ACME, c=FR
	 * dc=www, dc=acme, dc=com, c=US
	 * </pre>
	 * 
	 * If a wildcard is used for a value of an RDN, the value must be exactly *.
	 * The wildcard must match any value, and no substring matching must be
	 * done. For example:
	 * 
	 * <pre>
	 * cn=*,o=ACME,c=*
	 * </pre>
	 * 
	 * This match pattern with wildcard must match the following DNs:
	 * 
	 * <pre>
	 * cn=Bugs Bunny,o=ACME,c=US
	 * cn = Daffy Duck , o = ACME , c = US
	 * cn=Road Runner, o=ACME, c=NL
	 * </pre>
	 * 
	 * But not:
	 * 
	 * <pre>
	 * o=ACME, c=NL
	 * dc=acme.com, cn=Bugs Bunny, o=ACME, c=US
	 * </pre>
	 * 
	 * <p>
	 * A match pattern may contain a chain of DN match patterns. The semicolon(
	 * {@code ';'} &#92;u003B) must be used to separate DN match patterns in a
	 * chain. Wildcards can also be used to match against a complete DN within a
	 * chain.
	 * <p>
	 * The following example matches a certificate signed by Tweety Inc. in the
	 * US.
	 * </p>
	 * 
	 * <pre>
	 * * ; ou=S &amp; V, o=Tweety Inc., c=US
	 * </pre>
	 * <p>
	 * The wildcard ('*') matches zero or one DN in the chain, however,
	 * sometimes it is necessary to match a longer chain. The minus sign (
	 * {@code '-'} &#92;u002D) represents zero or more DNs, whereas the asterisk
	 * only represents a single DN. For example, to match a DN where the Tweety
	 * Inc. is in the DN chain, use the following expression:
	 * </p>
	 * 
	 * <pre>
	 * - ; *, o=Tweety Inc., c=US
	 * </pre>
	 * 
	 * @param matchPattern The pattern against which to match the DN chain.
	 * @param dnChain The DN chain to match against the specified pattern. Each
	 *        element of the chain must be of type {@code String} and use the
	 *        format defined in <a
	 *        href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>.
	 * @return {@code true} If the pattern matches the DN chain; otherwise
	 *         {@code false} is returned.
	 * @throws IllegalArgumentException If the specified match pattern or DN
	 *         chain is invalid.
	 * @since 1.5
	 */
	public static boolean matchDistinguishedNameChain(String matchPattern, List<String> dnChain) {
		return DNChainMatching.match(matchPattern, dnChain);
	}

	/**
	 * Return a {@code Bundle} for the specified bundle class loader.
	 * 
	 * @param bundleClassLoader A bundle class loader.
	 * @return An Optional containing {@code Bundle} for the specified bundle
	 *         class loader or an empty Optional if the specified class loader
	 *         is not associated with a specific bundle.
	 * @since 1.10
	 */
	public static Optional<Bundle> getBundle(ClassLoader bundleClassLoader) {
		requireNonNull(bundleClassLoader);
		return Optional
				.ofNullable((bundleClassLoader instanceof BundleReference)
						? ((BundleReference) bundleClassLoader).getBundle()
						: null);
	}

	/**
	 * Return a {@code Bundle} for the specified bundle class.
	 * 
	 * @param classFromBundle A class defined by a bundle.
	 * @return A {@code Bundle} for the specified bundle class or {@code null}
	 *         if the specified class was not defined by a bundle.
	 * @since 1.5
	 */
	public static Bundle getBundle(Class< ? > classFromBundle) {
		// We use doPriv since the caller may not have permission
		// to call getClassLoader.
		Optional<ClassLoader> cl = Optional
				.ofNullable(AccessController.doPrivileged(
						(PrivilegedAction<ClassLoader>) () -> classFromBundle
								.getClassLoader()));

		return cl.flatMap(FrameworkUtil::getBundle)
				.orElseGet(() -> helpers.stream()
						.map(helper -> helper.getBundle(classFromBundle))
						.filter(Optional::isPresent)
						.map(Optional::get)
						.findFirst()
						.orElse(null));
	}

	private final static List<FrameworkUtilHelper> helpers;
	static {
		List<FrameworkUtilHelper> l = new ArrayList<>();
		try {
			ServiceLoader<FrameworkUtilHelper> helperLoader = AccessController
					.doPrivileged(
							(PrivilegedAction<ServiceLoader<FrameworkUtilHelper>>) () -> ServiceLoader
									.load(FrameworkUtilHelper.class,
											FrameworkUtilHelper.class
													.getClassLoader()));
			helperLoader.forEach(l::add);
		} catch (Throwable error) {
			// try hard not to fail static <clinit>
			try {
				Thread t = Thread.currentThread();
				t.getUncaughtExceptionHandler().uncaughtException(t, error);
			} catch (Throwable ignored) {
				// we ignore this
			}
		}
		helpers = Collections.unmodifiableList(l);
	}

	/**
	 * This class contains a method to match a distinguished name (DN) chain
	 * against and DN chain pattern.
	 * <p>
	 * The format of DNs are given in RFC 2253. We represent a signature chain
	 * for an X.509 certificate as a semicolon separated list of DNs. This is
	 * what we refer to as the DN chain. Each DN is made up of relative
	 * distinguished names (RDN) which in turn are made up of key value pairs.
	 * For example:
	 * 
	 * <pre>
	 *   cn=ben+ou=research,o=ACME,c=us;ou=Super CA,c=CA
	 * </pre>
	 * 
	 * is made up of two DNs: "{@code cn=ben+ou=research,o=ACME,c=us} " and "
	 * {@code ou=Super CA,c=CA} ". The first DN is made of of three RDNs: "
	 * {@code cn=ben+ou=research}" and "{@code o=ACME}" and " {@code c=us}
	 * ". The first RDN has two name value pairs: " {@code cn=ben}" and "
	 * {@code ou=research}".
	 * <p>
	 * A chain pattern makes use of wildcards ('*' or '-') to match against DNs,
	 * and wildcards ('*') to match againts DN prefixes, and value. If a DN in a
	 * match pattern chain is made up of a wildcard ("*"), that wildcard will
	 * match zero or one DNs in the chain. If a DN in a match pattern chain is
	 * made up of a wildcard ("-"), that wildcard will match zero or more DNs in
	 * the chain. If the first RDN of a DN is the wildcard ("*"), that DN will
	 * match any other DN with the same suffix (the DN with the wildcard RDN
	 * removed). If a value of a name/value pair is a wildcard ("*"), the value
	 * will match any value for that name.
	 */
	static private final class DNChainMatching {
		private static final String	MINUS_WILDCARD	= "-";
		private static final String	STAR_WILDCARD	= "*";

		/**
		 * Check the name/value pairs of the rdn against the pattern.
		 * 
		 * @param rdn List of name value pairs for a given RDN.
		 * @param rdnPattern List of name value pattern pairs.
		 * @return true if the list of name value pairs match the pattern.
		 */
		private static boolean rdnmatch(List<?> rdn, List<?> rdnPattern) {
			if (rdn.size() != rdnPattern.size()) {
				return false;
			}
			for (int i = 0; i < rdn.size(); i++) {
				String rdnNameValue = (String) rdn.get(i);
				String patNameValue = (String) rdnPattern.get(i);
				int rdnNameEnd = rdnNameValue.indexOf('=');
				int patNameEnd = patNameValue.indexOf('=');
				if (rdnNameEnd != patNameEnd || !rdnNameValue.regionMatches(0, patNameValue, 0, rdnNameEnd)) {
					return false;
				}
				String patValue = patNameValue.substring(patNameEnd);
				String rdnValue = rdnNameValue.substring(rdnNameEnd);
				if (!rdnValue.equals(patValue) && !patValue.equals("=*") && !patValue.equals("=#16012a")) {
					return false;
				}
			}
			return true;
		}

		private static boolean dnmatch(List<?> dn, List<?> dnPattern) {
			int dnStart = 0;
			int patStart = 0;
			int patLen = dnPattern.size();
			if (patLen == 0) {
				return false;
			}
			if (dnPattern.get(0).equals(STAR_WILDCARD)) {
				patStart = 1;
				patLen--;
			}
			if (dn.size() < patLen) {
				return false;
			} else {
				if (dn.size() > patLen) {
					if (!dnPattern.get(0).equals(STAR_WILDCARD)) {
						// If the number of rdns do not match we must have a
						// prefix map
						return false;
					}
					// The rdnPattern and rdn must have the same number of
					// elements
					dnStart = dn.size() - patLen;
				}
			}
			for (int i = 0; i < patLen; i++) {
				if (!rdnmatch((List<?>) dn.get(i + dnStart), (List<?>) dnPattern.get(i + patStart))) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Parses a distinguished name chain pattern and returns a List where
		 * each element represents a distinguished name (DN) in the chain of
		 * DNs. Each element will be either a String, if the element represents
		 * a wildcard ("*" or "-"), or a List representing an RDN. Each element
		 * in the RDN List will be a String, if the element represents a
		 * wildcard ("*"), or a List of Strings, each String representing a
		 * name/value pair in the RDN.
		 * 
		 * @param pattern
		 * @return a list of DNs.
		 * @throws IllegalArgumentException
		 */
		private static List<Object> parseDNchainPattern(String pattern) {
			if (pattern == null) {
				throw new IllegalArgumentException("The pattern must not be null.");
			}
			List<Object> parsed = new ArrayList<Object>();
			final int length = pattern.length();
			char c = ';'; // start with semi-colon to detect empty pattern
			for (int startIndex = skipSpaces(pattern, 0); startIndex < length;) {
				int cursor = startIndex;
				int endIndex = startIndex;
				out: for (boolean inQuote = false; cursor < length; cursor++) {
					c = pattern.charAt(cursor);
					switch (c) {
						case '"' :
							inQuote = !inQuote;
							break;
						case '\\' :
							cursor++; // skip the escaped char
							if (cursor == length) {
								throw new IllegalArgumentException("unterminated escape");
							}
							break;
						case ';' :
							if (!inQuote) {
								break out; // end of pattern
							}
							break;
					}
					if (c != ' ') { // ignore trailing whitespace
						endIndex = cursor + 1;
					}
				}
				parsed.add(pattern.substring(startIndex, endIndex));
				startIndex = skipSpaces(pattern, cursor + 1);
			}
			if (c == ';') { // last non-whitespace character was a semi-colon
				throw new IllegalArgumentException("empty pattern");
			}

			// Now we have parsed into a list of strings, lets make List of rdn
			// out of them
			for (int i = 0; i < parsed.size(); i++) {
				String dn = (String) parsed.get(i);
				if (dn.equals(STAR_WILDCARD) || dn.equals(MINUS_WILDCARD)) {
					continue;
				}
				List<Object> rdns = new ArrayList<Object>();
				if (dn.charAt(0) == '*') {
					int index = skipSpaces(dn, 1);
					if (dn.charAt(index) != ',') {
						throw new IllegalArgumentException("invalid wildcard prefix");
					}
					rdns.add(STAR_WILDCARD);
					dn = new X500Principal(dn.substring(index + 1)).getName(X500Principal.CANONICAL);
				} else {
					dn = new X500Principal(dn).getName(X500Principal.CANONICAL);
				}
				// Now dn is a nice CANONICAL DN
				parseDN(dn, rdns);
				parsed.set(i, rdns);
			}
			return parsed;
		}

		private static List<Object> parseDNchain(List<String> chain) {
			if (chain == null) {
				throw new IllegalArgumentException("DN chain must not be null.");
			}
			List<Object> result = new ArrayList<Object>(chain.size());
			// Now we parse is a list of strings, lets make List of rdn out
			// of them
			for (String dn : chain) {
				dn = new X500Principal(dn).getName(X500Principal.CANONICAL);
				// Now dn is a nice CANONICAL DN
				List<Object> rdns = new ArrayList<Object>();
				parseDN(dn, rdns);
				result.add(rdns);
			}
			if (result.size() == 0) {
				throw new IllegalArgumentException("empty DN chain");
			}
			return result;
		}

		/**
		 * Increment startIndex until the end of dnChain is hit or until it is
		 * the index of a non-space character.
		 */
		private static int skipSpaces(String dnChain, int startIndex) {
			while (startIndex < dnChain.length() && dnChain.charAt(startIndex) == ' ') {
				startIndex++;
			}
			return startIndex;
		}

		/**
		 * Takes a distinguished name in canonical form and fills in the
		 * rdnArray with the extracted RDNs.
		 * 
		 * @param dn the distinguished name in canonical form.
		 * @param rdn the list to fill in with RDNs extracted from the dn
		 * @throws IllegalArgumentException if a formatting error is found.
		 */
		private static void parseDN(String dn, List<Object> rdn) {
			int startIndex = 0;
			char c = '\0';
			List<String> nameValues = new ArrayList<String>();
			while (startIndex < dn.length()) {
				int endIndex;
				for (endIndex = startIndex; endIndex < dn.length(); endIndex++) {
					c = dn.charAt(endIndex);
					if (c == ',' || c == '+') {
						break;
					}
					if (c == '\\') {
						endIndex++; // skip the escaped char
					}
				}
				if (endIndex > dn.length()) {
					throw new IllegalArgumentException("unterminated escape " + dn);
				}
				nameValues.add(dn.substring(startIndex, endIndex));
				if (c != '+') {
					rdn.add(nameValues);
					if (endIndex != dn.length()) {
						nameValues = new ArrayList<String>();
					} else {
						nameValues = null;
					}
				}
				startIndex = endIndex + 1;
			}
			if (nameValues != null) {
				throw new IllegalArgumentException("improperly terminated DN " + dn);
			}
		}

		/**
		 * This method will return an 'index' which points to a non-wildcard DN
		 * or the end-of-list.
		 */
		private static int skipWildCards(List<Object> dnChainPattern, int dnChainPatternIndex) {
			int i;
			for (i = dnChainPatternIndex; i < dnChainPattern.size(); i++) {
				Object dnPattern = dnChainPattern.get(i);
				if (dnPattern instanceof String) {
					if (!dnPattern.equals(STAR_WILDCARD) && !dnPattern.equals(MINUS_WILDCARD)) {
						throw new IllegalArgumentException("expected wildcard in DN pattern");
					}
					// otherwise continue skipping over wild cards
				} else {
					if (dnPattern instanceof List<?>) {
						// if its a list then we have our 'non-wildcard' DN
						break;
					} else {
						// unknown member of the DNChainPattern
						throw new IllegalArgumentException("expected String or List in DN Pattern");
					}
				}
			}
			// i either points to end-of-list, or to the first
			// non-wildcard pattern after dnChainPatternIndex
			return i;
		}

		/**
		 * recursively attempt to match the DNChain, and the DNChainPattern
		 * where DNChain is of the format: "DN;DN;DN;" and DNChainPattern is of
		 * the format: "DNPattern;*;DNPattern" (or combinations of this)
		 */
		private static boolean dnChainMatch(List<Object> dnChain, int dnChainIndex, List<Object> dnChainPattern, int dnChainPatternIndex) throws IllegalArgumentException {
			if (dnChainIndex >= dnChain.size()) {
				return false;
			}
			if (dnChainPatternIndex >= dnChainPattern.size()) {
				return false;
			}
			// check to see what the pattern starts with
			Object dnPattern = dnChainPattern.get(dnChainPatternIndex);
			if (dnPattern instanceof String) {
				if (!dnPattern.equals(STAR_WILDCARD) && !dnPattern.equals(MINUS_WILDCARD)) {
					throw new IllegalArgumentException("expected wildcard in DN pattern");
				}
				// here we are processing a wild card as the first DN
				// skip all wildcard DN's
				if (dnPattern.equals(MINUS_WILDCARD)) {
					dnChainPatternIndex = skipWildCards(dnChainPattern, dnChainPatternIndex);
				} else {
					dnChainPatternIndex++; // only skip the '*' wildcard
				}
				if (dnChainPatternIndex >= dnChainPattern.size()) {
					// return true iff the wild card is '-' or if we are at the
					// end of the chain
					return dnPattern.equals(MINUS_WILDCARD) ? true : dnChain.size() - 1 == dnChainIndex;
				}
				//
				// we will now recursively call to see if the rest of the
				// DNChainPattern matches increasingly smaller portions of the
				// rest of the DNChain
				//
				if (dnPattern.equals(STAR_WILDCARD)) {
					// '*' option: only wildcard on 0 or 1
					return dnChainMatch(dnChain, dnChainIndex, dnChainPattern, dnChainPatternIndex) || dnChainMatch(dnChain, dnChainIndex + 1, dnChainPattern, dnChainPatternIndex);
				}
				for (int i = dnChainIndex; i < dnChain.size(); i++) {
					// '-' option: wildcard 0 or more
					if (dnChainMatch(dnChain, i, dnChainPattern, dnChainPatternIndex)) {
						return true;
					}
				}
				// if we are here, then we didn't find a match.. fall through to
				// failure
			} else {
				if (dnPattern instanceof List<?>) {
					// here we have to do a deeper check for each DN in the
					// pattern until we hit a wild card
					do {
						if (!dnmatch((List<?>) dnChain.get(dnChainIndex), (List<?>) dnPattern)) {
							return false;
						}
						// go to the next set of DN's in both chains
						dnChainIndex++;
						dnChainPatternIndex++;
						// if we finished the pattern then it all matched
						if ((dnChainIndex >= dnChain.size()) && (dnChainPatternIndex >= dnChainPattern.size())) {
							return true;
						}
						// if the DN Chain is finished, but the pattern isn't
						// finished then if the rest of the pattern is not
						// wildcard then we are done
						if (dnChainIndex >= dnChain.size()) {
							dnChainPatternIndex = skipWildCards(dnChainPattern, dnChainPatternIndex);
							// return TRUE iff the pattern index moved past the
							// list-size (implying that the rest of the pattern
							// is all wildcards)
							return dnChainPatternIndex >= dnChainPattern.size();
						}
						// if the pattern finished, but the chain continues then
						// we have a mis-match
						if (dnChainPatternIndex >= dnChainPattern.size()) {
							return false;
						}
						// get the next DN Pattern
						dnPattern = dnChainPattern.get(dnChainPatternIndex);
						if (dnPattern instanceof String) {
							if (!dnPattern.equals(STAR_WILDCARD) && !dnPattern.equals(MINUS_WILDCARD)) {
								throw new IllegalArgumentException("expected wildcard in DN pattern");
							}
							// if the next DN is a 'wildcard', then we will
							// recurse
							return dnChainMatch(dnChain, dnChainIndex, dnChainPattern, dnChainPatternIndex);
						} else {
							if (!(dnPattern instanceof List<?>)) {
								throw new IllegalArgumentException("expected String or List in DN Pattern");
							}
						}
						// if we are here, then we will just continue to the
						// match the next set of DN's from the DNChain, and the
						// DNChainPattern since both are lists
					} while (true);
					// should never reach here?
				} else {
					throw new IllegalArgumentException("expected String or List in DN Pattern");
				}
			}
			// if we get here, the the default return is 'mis-match'
			return false;
		}

		/**
		 * Matches a distinguished name chain against a pattern of a
		 * distinguished name chain.
		 * 
		 * @param dnChain
		 * @param pattern the pattern of distinguished name (DN) chains to match
		 *        against the dnChain. Wildcards ("*" or "-") can be used in
		 *        three cases:
		 *        <ol>
		 *        <li>As a DN. In this case, the DN will consist of just the "*"
		 *        or "-". When "*" is used it will match zero or one DNs. When
		 *        "-" is used it will match zero or more DNs. For example,
		 *        "cn=me,c=US;*;cn=you" will match
		 *        "cn=me,c=US";cn=you" and "cn=me,c=US;cn=her;cn=you". The
		 *        pattern "cn=me,c=US;-;cn=you" will match "cn=me,c=US";cn=you"
		 *        and "cn=me,c=US;cn=her;cn=him;cn=you".</li>
		 *        <li>As a DN prefix. In this case, the DN must start with "*,".
		 *        The wild card will match zero or more RDNs at the start of a
		 *        DN. For example, "*,cn=me,c=US;cn=you" will match
		 *        "cn=me,c=US";cn=you" and
		 *        "ou=my org unit,o=my org,cn=me,c=US;cn=you"</li>
		 *        <li>As a value. In this case the value of a name value pair in
		 *        an RDN will be a "*". The wildcard will match any value for
		 *        the given name. For example, "cn=*,c=US;cn=you" will match
		 *        "cn=me,c=US";cn=you" and "cn=her,c=US;cn=you", but it will not
		 *        match "ou=my org unit,c=US;cn=you". If the wildcard does not
		 *        occur by itself in the value, it will not be used as a
		 *        wildcard. In other words, "cn=m*,c=US;cn=you" represents the
		 *        common name of "m*" not any common name starting with "m".</li>
		 *        </ol>
		 * @return true if dnChain matches the pattern.
		 * @throws IllegalArgumentException
		 */
		static boolean match(String pattern, List<String> dnChain) {
			List<Object> parsedDNChain;
			List<Object> parsedDNPattern;
			try {
				parsedDNChain = parseDNchain(dnChain);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException(
						"Invalid DN chain: " + toString(dnChain), e);
			}
			try {
				parsedDNPattern = parseDNchainPattern(pattern);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException(
						"Invalid match pattern: " + pattern, e);
			}
			return dnChainMatch(parsedDNChain, 0, parsedDNPattern, 0);
		}

		private static String toString(List<?> dnChain) {
			if (dnChain == null) {
				return null;
			}
			StringBuilder sb = new StringBuilder();
			for (Iterator<?> iChain = dnChain.iterator(); iChain.hasNext();) {
				sb.append(iChain.next());
				if (iChain.hasNext()) {
					sb.append("; ");
				}
			}
			return sb.toString();
		}
	}

	/**
	 * Return a Map wrapper around a Dictionary.
	 *
	 * @param <K> The type of the key.
	 * @param <V> The type of the value.
	 * @param dictionary The dictionary to wrap.
	 * @return A Map object which wraps the specified dictionary. If the
	 *         specified dictionary can be cast to a Map, then the specified
	 *         dictionary is returned.
	 * @since 1.10
	 */
	public static <K, V> Map<K,V> asMap(
			Dictionary< ? extends K, ? extends V> dictionary) {
		if (dictionary instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<K,V> coerced = (Map<K,V>) dictionary;
			return coerced;
		}
		return new DictionaryAsMap<>(dictionary);
	}

	private static class DictionaryAsMap<K, V> extends AbstractMap<K,V> {
		private final Dictionary<K,V> dict;

		@SuppressWarnings("unchecked")
		DictionaryAsMap(Dictionary< ? extends K, ? extends V> dict) {
			this.dict = (Dictionary<K,V>) requireNonNull(dict);
		}

		Iterator<K> keys() {
			List<K> keys = new ArrayList<>(dict.size());
			for (Enumeration<K> e = dict.keys(); e.hasMoreElements();) {
				keys.add(e.nextElement());
			}
			return keys.iterator();
		}

		@Override
		public int size() {
			return dict.size();
		}

		@Override
		public boolean isEmpty() {
			return dict.isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			if (key == null) {
				return false;
			}
			return dict.get(key) != null;
		}

		@Override
		public V get(Object key) {
			if (key == null) {
				return null;
			}
			return dict.get(key);
		}

		@Override
		public V put(K key, V value) {
			return dict.put(
					requireNonNull(key,
							"a Dictionary cannot contain a null key"),
					requireNonNull(value,
							"a Dictionary cannot contain a null value"));
		}

		@Override
		public V remove(Object key) {
			if (key == null) {
				return null;
			}
			return dict.remove(key);
		}

		@Override
		public void clear() {
			for (Iterator<K> iter = keys(); iter.hasNext();) {
				dict.remove(iter.next());
			}
		}

		@Override
		public Set<K> keySet() {
			return new KeySet();
		}

		@Override
		public Set<Map.Entry<K,V>> entrySet() {
			return new EntrySet();
		}

		@Override
		public String toString() {
			return dict.toString();
		}

		final class KeySet extends AbstractSet<K> {
			@Override
			public Iterator<K> iterator() {
				return new KeyIterator();
			}

			@Override
			public int size() {
				return DictionaryAsMap.this.size();
			}

			@Override
			public boolean isEmpty() {
				return DictionaryAsMap.this.isEmpty();
			}

			@Override
			public boolean contains(Object key) {
				return DictionaryAsMap.this.containsKey(key);
			}

			@Override
			public boolean remove(Object key) {
				return DictionaryAsMap.this.remove(key) != null;
			}

			@Override
			public void clear() {
				DictionaryAsMap.this.clear();
			}
		}

		final class KeyIterator implements Iterator<K> {
			private final Iterator<K>	keys	= DictionaryAsMap.this.keys();
			private K					key		= null;

			@Override
			public boolean hasNext() {
				return keys.hasNext();
			}

			@Override
			public K next() {
				return key = keys.next();
			}

			@Override
			public void remove() {
				if (key == null) {
					throw new IllegalStateException();
				}
				DictionaryAsMap.this.remove(key);
				key = null;
			}
		}

		final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
			@Override
			public Iterator<Map.Entry<K,V>> iterator() {
				return new EntryIterator();
			}

			@Override
			public int size() {
				return DictionaryAsMap.this.size();
			}

			@Override
			public boolean isEmpty() {
				return DictionaryAsMap.this.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				if (o instanceof Map.Entry) {
					Map.Entry< ? , ? > e = (Map.Entry< ? , ? >) o;
					return containsEntry(e);
				}
				return false;
			}

			private boolean containsEntry(Map.Entry< ? , ? > e) {
				Object key = e.getKey();
				if (key == null) {
					return false;
				}
				Object value = e.getValue();
				if (value == null) {
					return false;
				}
				return Objects.equals(DictionaryAsMap.this.get(key), value);
			}

			@Override
			public boolean remove(Object o) {
				if (o instanceof Map.Entry) {
					Map.Entry< ? , ? > e = (Map.Entry< ? , ? >) o;
					if (containsEntry(e)) {
						DictionaryAsMap.this.remove(e.getKey());
						return true;
					}
				}
				return false;
			}

			@Override
			public void clear() {
				DictionaryAsMap.this.clear();
			}
		}

		final class EntryIterator implements Iterator<Map.Entry<K,V>> {
			private final Iterator<K>	keys	= DictionaryAsMap.this.keys();
			private K					key		= null;

			@Override
			public boolean hasNext() {
				return keys.hasNext();
			}

			@Override
			public Map.Entry<K,V> next() {
				return new Entry(key = keys.next());
			}

			@Override
			public void remove() {
				if (key == null) {
					throw new IllegalStateException();
				}
				DictionaryAsMap.this.remove(key);
				key = null;
			}
		}

		final class Entry extends SimpleEntry<K,V> {
			private static final long serialVersionUID = 1L;

			Entry(K key) {
				super(key, DictionaryAsMap.this.get(key));
			}

			@Override
			public V setValue(V value) {
				DictionaryAsMap.this.put(getKey(), value);
				return super.setValue(value);
			}
		}
	}

	/**
	 * Return a Dictionary wrapper around a Map.
	 *
	 * @param <K> The type of the key.
	 * @param <V> The type of the value.
	 * @param map The map to wrap.
	 * @return A Dictionary object which wraps the specified map. If the
	 *         specified map can be cast to a Dictionary, then the specified map
	 *         is returned.
	 * @since 1.10
	 */
	public static <K, V> Dictionary<K,V> asDictionary(
			Map< ? extends K, ? extends V> map) {
		if (map instanceof Dictionary) {
			@SuppressWarnings("unchecked")
			Dictionary<K,V> coerced = (Dictionary<K,V>) map;
			return coerced;
		}
		return new MapAsDictionary<>(map);
	}

	private static class MapAsDictionary<K, V> extends Dictionary<K,V> {
		private final Map<K,V> map;

		@SuppressWarnings("unchecked")
		MapAsDictionary(Map< ? extends K, ? extends V> map) {
			this.map = (Map<K,V>) requireNonNull(map);
			boolean nullKey;
			try {
				nullKey = map.containsKey(null);
			} catch (NullPointerException e) {
				nullKey = false; // map does not allow null key
			}
			if (nullKey) {
				throw new NullPointerException(
						"a Dictionary cannot contain a null key");
			}
			boolean nullValue;
			try {
				nullValue = map.containsValue(null);
			} catch (NullPointerException e) {
				nullValue = false; // map does not allow null value
			}
			if (nullValue) {
				throw new NullPointerException(
						"a Dictionary cannot contain a null value");
			}
		}

		@Override
		public int size() {
			return map.size();
		}

		@Override
		public boolean isEmpty() {
			return map.isEmpty();
		}

		@Override
		public Enumeration<K> keys() {
			return Collections.enumeration(map.keySet());
		}

		@Override
		public Enumeration<V> elements() {
			return Collections.enumeration(map.values());
		}

		@Override
		public V get(Object key) {
			if (key == null) {
				return null;
			}
			return map.get(key);
		}

		@Override
		public V put(K key, V value) {
			return map.put(
					requireNonNull(key,
							"a Dictionary cannot contain a null key"),
					requireNonNull(value,
							"a Dictionary cannot contain a null value"));
		}

		@Override
		public V remove(Object key) {
			if (key == null) {
				return null;
			}
			return map.remove(key);
		}

		@Override
		public String toString() {
			return map.toString();
		}
	}

}
