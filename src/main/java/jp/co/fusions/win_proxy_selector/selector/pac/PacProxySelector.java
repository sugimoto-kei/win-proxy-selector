package jp.co.fusions.win_proxy_selector.selector.pac;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import jp.co.fusions.win_proxy_selector.util.Logger;
import jp.co.fusions.win_proxy_selector.util.Logger.LogLevel;
import jp.co.fusions.win_proxy_selector.util.ProxyUtil;

/*****************************************************************************
 * ProxySelector that will use a PAC script to find an proxy for a given URI.
 *
 * @author Markus Bernhardt, Copyright 2016
 * @author Bernd Rosstauscher, Copyright 2009
 ****************************************************************************/
public class PacProxySelector extends ProxySelector {

	// private static final String PAC_PROXY = "PROXY";
	private static final String PAC_SOCKS = "SOCKS";
	private static final String PAC_DIRECT = "DIRECT";

	private PacScriptParser pacScriptParser;

	private static volatile boolean enabled = true;

	/*************************************************************************
	 * Constructor
	 *
	 * @param pacSource
	 *          the source for the PAC file.
	 ************************************************************************/

	public PacProxySelector(PacScriptSource pacSource) {
		super();
		selectEngine(pacSource);
	}

	/*************************************************************************
	 * Can be used to enable / disable the proxy selector. If disabled it will
	 * return DIRECT for all urls.
	 *
	 * @param enable
	 *          the new status to set.
	 ************************************************************************/

	public static void setEnabled(boolean enable) {
		enabled = enable;
	}

	/*************************************************************************
	 * Checks if the selector is currently enabled.
	 *
	 * @return true if enabled else false.
	 ************************************************************************/

	public static boolean isEnabled() {
		return enabled;
	}

	/*************************************************************************
	 * Selects one of the available PAC parser engines.
	 *
	 * @param pacSource
	 *          to use as input.
	 ************************************************************************/

	private void selectEngine(PacScriptSource pacSource) {
		try {
			Logger.log(getClass(), LogLevel.INFO, "Using javax.script JavaScript engine.");
			pacScriptParser = new JavaxPacScriptParser(pacSource);
		} catch (Exception e) {
			Logger.log(getClass(), LogLevel.ERROR, "PAC parser error.", e);
		}
	}

	/*************************************************************************
	 * connectFailed
	 *
	 * @see java.net.ProxySelector#connectFailed(java.net.URI,
	 *      java.net.SocketAddress, java.io.IOException)
	 ************************************************************************/
	@Override
	public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
		// Not used.
	}

	/*************************************************************************
	 * select
	 *
	 * @see java.net.ProxySelector#select(java.net.URI)
	 ************************************************************************/
	@Override
	public List<Proxy> select(URI uri) {
		if (uri == null) {
			throw new IllegalArgumentException("URI must not be null.");
		}

		// Fix for Java 1.6.16+ where we get a infinite loop because
		// URL.connect(Proxy.NO_PROXY) does not work as expected.
		if (!enabled) {
			return ProxyUtil.noProxyList();
		}

		return findProxy(uri);
	}

	/*************************************************************************
	 * Evaluation of the given URL with the PAC-file.
	 *
	 * Two cases can be handled here: DIRECT Fetch the object directly from the
	 * content HTTP server denoted by its URL PROXY name:port Fetch the object via
	 * the proxy HTTP server at the given location (name and port)
	 *
	 * @param uri
	 *          <code>URI</code> to be evaluated.
	 * @return <code>Proxy</code>-object list as result of the evaluation.
	 ************************************************************************/

	private List<Proxy> findProxy(URI uri) {
		try {
			if (pacScriptParser == null) {
				return ProxyUtil.noProxyList();
			}
			String parseResult = pacScriptParser.evaluate(uri.toString(), uri.getHost());
			if (parseResult == null) {
				return ProxyUtil.noProxyList();
			}
			List<Proxy> proxies = new ArrayList<Proxy>();
			String[] proxyDefinitions = parseResult.split("[;]");
			for (String proxyDef : proxyDefinitions) {
				if (proxyDef.trim().length() > 0) {
					proxies.add(buildProxyFromPacResult(proxyDef));
				}
			}
			return proxies;
		} catch (ProxyEvaluationException e) {
			Logger.log(getClass(), LogLevel.ERROR, "PAC JavaScript evaluation error. \n{0}\n{1}", e.getScript(),e);
			return ProxyUtil.noProxyList();
		}
	}

	/*************************************************************************
	 * The proxy evaluator will return a proxy string. This method will take this
	 * string and build a matching <code>Proxy</code> for it.
	 *
	 * @param pacResult
	 *          the result from the PAC parser.
	 * @return a Proxy
	 ************************************************************************/

	static Proxy buildProxyFromPacResult(String pacResult) {
		String[] words = pacResult.trim().split("\\s+");
		if (words.length == 0) return Proxy.NO_PROXY;

		// Check proxy type.
		Proxy.Type type = toProxyType(words[0]);
		if (type == Proxy.Type.DIRECT) return Proxy.NO_PROXY;
		if (words.length < 2) return Proxy.NO_PROXY;

		SocketAddress adr = toSocketAddress(concat(words,1));

		return new Proxy(type, adr);
	}
	private static String concat(String[] strings, int startIndex){
		StringBuilder b = new StringBuilder();
		for (int i = startIndex; i < strings.length; i++) {
			b.append(strings[i]);
		}
		return b.toString();
	}
	private static Proxy.Type toProxyType(String string) {
		String proxyType = string.toUpperCase().trim();

		if (proxyType.startsWith(PAC_DIRECT)) {
			return Proxy.Type.DIRECT;
		}
		if (proxyType.startsWith(PAC_SOCKS)) {
			// SOCKS, SOCKS4, SOCKS5
			return Proxy.Type.SOCKS;
		}
		// PROXY, HTTP, HTTPS
		return Proxy.Type.HTTP;
	}

	private static SocketAddress toSocketAddress(String string) {
		String hostAndPort = string.trim();

		// Split port from host
		int indexOfPortSeparator = hostAndPort.lastIndexOf(':');
		int indexOfTheEndOfIPv6Address = hostAndPort.lastIndexOf(']');
		if (indexOfPortSeparator != -1) {
			if (indexOfTheEndOfIPv6Address < indexOfPortSeparator) {
				// such as [2001:db8:85a3:8d3:1319:8a2e:370:7348]:3128
				return new InetSocketAddress(
					hostAndPort.substring(0, indexOfPortSeparator).trim(),
					Integer.parseInt(hostAndPort.substring(indexOfPortSeparator + 1).trim()));
			} else {
				// such as [2001:db8:85a3:8d3:1319:8a2e:370:7348]  ... The last colon was misjudged as the port separator.
				// So, use default port
				return new InetSocketAddress(
					hostAndPort,
					ProxyUtil.DEFAULT_PROXY_PORT);
			}
		} else {
			return new InetSocketAddress(
				hostAndPort,
				ProxyUtil.DEFAULT_PROXY_PORT);
		}
	}
}
