package de.cheffe.solrsample.rule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.FieldAnalysisRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.CoreContainer;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Resource for solr integration tests. It will use the configuration stored
 * within a given solr.xml to start up.
 * </p>
 * <p>
 * To manage the content of the index use
 * </p>
 * <ul>
 * <li>{@link #addBeanToIndex(Object)}
 * <li>{@link #addBeansToIndex(List)}
 * <li>{@link #clearIndex()}
 * </ul>
 * <p>
 * To query for documents use
 * </p>
 * <ul>
 * <li> {@link #query(String)}
 * </ul>
 * <p>
 * To test analyzers use
 * </p>
 * <ul>
 * <li>analysis at index time {@link #analyseIndexTime(String, String)}
 * <li>analysis at query time {@link #analyseQueryTime(String, String)}
 * </ul>
 * 
 * @author cheffe
 * 
 * @param <T>
 */
public class EmbeddedSolrTestHarness<T extends Object> extends ExternalResource {

	private static final Logger LOG = LoggerFactory
			.getLogger(EmbeddedSolrTestHarness.class);

	public EmbeddedSolrServer server;
	private CoreContainer container;
	private String pathToSolrXml;
	private String defaultCore;

	/**
	 * Defines that the solr.xml is to be found within resource package 'solr'.
	 */
	public EmbeddedSolrTestHarness() {
		this(null, "/solr/solr.xml");
	}

	/**
	 * @param aDefaultCore
	 *            the core to use as default
	 */
	public EmbeddedSolrTestHarness(String aDefaultCore) {
		this(aDefaultCore, "/solr/solr.xml");
	}

	/**
	 * @para aDefaultCore the core to use as default
	 * @param aPathToSolrXml
	 *            the path to the solr.xml within your resources - e.g.
	 *            "/solr/solr.xml" if a solr.xml is to be found within a package
	 *            named 'solr'
	 */
	public EmbeddedSolrTestHarness(String aDefaultCore, String aPathToSolrXml) {
		super();
		defaultCore = aDefaultCore;
		pathToSolrXml = aPathToSolrXml;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		LOG.info("start embedded solr");
		String tmpSolrXmlPath = EmbeddedSolrTestHarness.class.getResource(
				pathToSolrXml).getFile();
		File tmpSolrXml = new File(tmpSolrXmlPath);
		File tmpSolrHomeDir = tmpSolrXml.getParentFile();
		container = CoreContainer.createAndLoad(tmpSolrHomeDir.getAbsolutePath(), tmpSolrXml);
		if (defaultCore == null) {
			defaultCore = container.getDefaultCoreName();
		}
		server = new EmbeddedSolrServer(container, defaultCore);
		clearIndex();
	}

	@Override
	protected void after() {
		super.after();
		LOG.info("shutdown embedded solr");
		server.shutdown();
		container.shutdown();
	}

	/**
	 * Adds a given list of beans to the index and commits them.
	 * 
	 * @param aBeans
	 *            the beans to add
	 */
	public void addBeanToIndex(T aBean) {
		LOG.info("adding document to index " + aBean);
		try {
			server.addBean(aBean);
			server.commit();
		} catch (IOException | SolrServerException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Adds a given list of beans to the index and commits them.
	 * 
	 * @param aBeans
	 *            the beans to add
	 */
	public void addBeansToIndex(List<T> aBeans) {
		LOG.info("adding documents to index " + aBeans);
		try {
			server.addBeans(aBeans);
			server.commit();
		} catch (SolrServerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Delete all documents from the index.
	 */
	public void clearIndex() {
		LOG.info("clearing index");
		try {
			server.deleteByQuery("*:*");
			server.commit();
		} catch (SolrServerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param aQuery
	 *            the query to ask the server for
	 * @return the list of T that are contained in the
	 *         response
	 */
	public List<T> query(String aQuery, Class<T> aClass) {
		SolrQuery tmpQuery = new SolrQuery(aQuery);
		try {
			QueryResponse tmpResponse = server.query(tmpQuery);
			return (List<T>) tmpResponse.getBeans(aClass);
		} catch (SolrServerException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param aQuery
	 *            the query to ask the server for
	 * @return the list of T that are contained in the
	 *         response
	 */
	public List<T> query(SolrQuery aQuery, Class<T> aClass) {
		try {
			QueryResponse tmpResponse = server.query(aQuery);
			return (List<T>) tmpResponse.getBeans(aClass);
		} catch (SolrServerException e) {
			throw new RuntimeException(e);
		}
	}

    /**
     * @param aQuery
     *            the query to ask the server for
     * @return the QueryResponse
     */
    public QueryResponse query(String aQuery) {
        SolrQuery tmpQuery = new SolrQuery(aQuery);
        try {
            return server.query(tmpQuery);
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        }
    }
	
	/**
	 * @param aFieldType
	 *            the name of the field type whose analyser shall be taken to
	 *            compute the given raw text
	 * @param aRawText
	 *            the raw text to analyse
	 * @return the tokens that would be placed in the index
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<String> analyseIndexTime(String aFieldType, String aRawText)
			throws SolrServerException, IOException {
		FieldAnalysisRequest tmpRequest = new FieldAnalysisRequest();
		tmpRequest.setFieldTypes(Arrays.asList(aFieldType));
		tmpRequest.setFieldValue(aRawText);

		NamedList<Object> tmpResponse = server.request(tmpRequest);
		tmpResponse = (NamedList<Object>) tmpResponse.get("analysis");
		tmpResponse = (NamedList<Object>) tmpResponse.get("field_types");
		tmpResponse = (NamedList<Object>) tmpResponse.get(aFieldType);
		tmpResponse = (NamedList<Object>) tmpResponse.get("index");
		int tmpSize = tmpResponse.size() - 1;
		ArrayList<SimpleOrderedMap> tmpList = (ArrayList<SimpleOrderedMap>) tmpResponse
				.getVal(tmpSize);
		ArrayList<String> tmpResult = new ArrayList<String>(tmpList.size());
		for (SimpleOrderedMap tmpObj : tmpList) {
			tmpResult.add(tmpObj.get("text").toString());
		}
		return tmpResult;
	}

	/**
	 * @param aFieldType
	 *            the name of the field type whose analyser shall be taken to
	 *            compute the given raw text
	 * @param aRawText
	 *            the raw text to analyse
	 * @return the tokens that would used to query against the index
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<String> analyseQueryTime(String aFieldType, String aRawText)
			throws SolrServerException, IOException {
		FieldAnalysisRequest tmpRequest = new FieldAnalysisRequest();
		tmpRequest.setFieldTypes(Arrays.asList(aFieldType));
		tmpRequest.setQuery(aRawText);

		NamedList<Object> tmpResponse = server.request(tmpRequest);
		tmpResponse = (NamedList<Object>) tmpResponse.get("analysis");
		tmpResponse = (NamedList<Object>) tmpResponse.get("field_types");
		tmpResponse = (NamedList<Object>) tmpResponse.get(aFieldType);
		tmpResponse = (NamedList<Object>) tmpResponse.get("query");
		int tmpSize = tmpResponse.size() - 1;
		ArrayList<SimpleOrderedMap> tmpList = (ArrayList<SimpleOrderedMap>) tmpResponse
				.getVal(tmpSize);
		ArrayList<String> tmpResult = new ArrayList<String>(tmpList.size());
		for (SimpleOrderedMap tmpObj : tmpList) {
			tmpResult.add(tmpObj.get("text").toString());
		}
		return tmpResult;
	}
	
	public void runDataImportHandler(String aHandlerName) throws Exception {
        ModifiableSolrParams tmpImportParams = new ModifiableSolrParams();
        tmpImportParams.set("command", "full-import");
        tmpImportParams.set("clean", true);
        tmpImportParams.set("commit", true);
        tmpImportParams.set("optimize", false);
        
        UpdateRequest tmpRequest = new UpdateRequest(aHandlerName);
        tmpRequest.setParams(tmpImportParams);
        tmpRequest.process(server);
        
        ModifiableSolrParams tmpStatusParams = new ModifiableSolrParams();
        tmpStatusParams.set("command", "status");
        String tmpStatus = "";
        do {
          LOG.info("waiting for import to finish, status was " + tmpStatus);
          Thread.sleep(100);
          UpdateRequest tmpStatusRequest = new UpdateRequest(aHandlerName);
          tmpStatusRequest.setParams(tmpStatusParams);
          UpdateResponse tmpStatusResponse = tmpStatusRequest.process(server);
          tmpStatus = tmpStatusResponse.getResponse().get("status").toString();
          LOG.info("status is: " + tmpStatus);
        } while ("busy".equals(tmpStatus));
        LOG.info("import done");
	}

}