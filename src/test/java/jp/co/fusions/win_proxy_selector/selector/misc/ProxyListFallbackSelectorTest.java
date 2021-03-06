package jp.co.fusions.win_proxy_selector.selector.misc;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import jp.co.fusions.win_proxy_selector.TestUtil;

/*****************************************************************************
 * Unit Tests for the ProxyListFallbackSelector
 * 
 * @author Markus Bernhardt, Copyright 2016
 * @author Bernd Rosstauscher, Copyright 2009
 ****************************************************************************/

public class ProxyListFallbackSelectorTest {

	private ProxyListFallbackSelector selector;

	/*************************************************************************
	 * Setup before tests.
	 ************************************************************************/
	@Before
	public void setup() {
		this.selector = new ProxyListFallbackSelector(new ProxySelector() {
			@Override
			public List<Proxy> select(URI uri) {
				return Arrays.asList(TestUtil.HTTP_TEST_PROXY, TestUtil.HTTPS_TEST_PROXY);
			}

			@Override
			public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
				// Not used on the delegate
			}
		});
	}

	/*************************************************************************
	 * Test method
	 ************************************************************************/
	@Test
	public void testList() {
		List<Proxy> result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(2, result.size());
		assertEquals(TestUtil.HTTP_TEST_PROXY, result.get(0));
		assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(1));
	}

	/*************************************************************************
	 * Test method
	 ************************************************************************/
	@Test
	public void testFailedProxy() {
		this.selector.connectFailed(TestUtil.HTTP_TEST_URI, TestUtil.HTTP_TEST_PROXY.address(),
		        new IOException("TEST"));

		List<Proxy> result = this.selector.select(TestUtil.HTTP_TEST_URI);

        assertEquals(1, result.size());
		assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(0));
	}

    /*************************************************************************
     * Test method
     * 
     * @throws InterruptedException
     *             if the test wait period was interrupted
     ************************************************************************/
    @Test
    public void testFailedProxyRetry() throws InterruptedException {
        this.selector.setRetryAfterMs(100);
        this.selector.connectFailed(TestUtil.HTTP_TEST_URI, TestUtil.HTTP_TEST_PROXY.address(),
                new IOException("TEST"));

        List<Proxy> result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(1, result.size());
        assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(0));

        Thread.sleep(200);
        result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(2, result.size());
        assertEquals(TestUtil.HTTP_TEST_PROXY, result.get(0));
        assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(1));
    }

    /*************************************************************************
     * Test method
     * 
     * @throws InterruptedException
     *             if the test wait period was interrupted
     ************************************************************************/
    @Test
    public void testFailedProxyFallbackToNoProxy() throws InterruptedException {
        this.selector.setRetryAfterMs(100);
        this.selector.connectFailed(TestUtil.HTTP_TEST_URI, TestUtil.HTTP_TEST_PROXY.address(),
                new IOException("TEST"));

        List<Proxy> result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(1, result.size());
        assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(0));

        this.selector.connectFailed(TestUtil.HTTPS_TEST_URI, TestUtil.HTTPS_TEST_PROXY.address(),
                new IOException("TEST"));

        result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(1, result.size());
        assertEquals(Proxy.NO_PROXY, result.get(0));

        Thread.sleep(200);
        result = this.selector.select(TestUtil.HTTP_TEST_URI);
        assertEquals(2, result.size());
        assertEquals(TestUtil.HTTP_TEST_PROXY, result.get(0));
        assertEquals(TestUtil.HTTPS_TEST_PROXY, result.get(1));
    }

}
