package com.commerce.radar.parser;

import com.commerce.radar.parser.model.IssueKind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueClassifierTest {

    @Test
    void classifiesKnownHybrisFamilies() {
        assertEquals(IssueKind.CRONJOB, IssueClassifier.classify(
                "solrIncrementalUpdateCronJob", "CronJobPerformable", "CronJob failed", "ModelNotFoundException", ""));
        assertEquals(IssueKind.IMPEX, IssueClassifier.classify(
                "ImpExReader", "impex reader", "unknown catalog version", "ImpExException", "products-delta.impex"));
        assertEquals(IssueKind.OCC, IssueClassifier.classify(
                "DefaultCartFacade", "hybrisHTTP23", "add to cart", "NullPointerException",
                "de.hybris.platform.commercewebservices.core.v2.controller.CartsController"));
        assertEquals(IssueKind.SOLR, IssueClassifier.classify(
                "SolrIndexerHotUpdateHandler", "solr indexer", "Solr ping failed", "", ""));
        assertEquals(IssueKind.FLEXIBLE_SEARCH, IssueClassifier.classify(
                "DefaultFlexibleSearchService", "hybrisHTTP1", "FlexibleSearch failed", "FlexibleSearchException", ""));
        assertEquals(IssueKind.INTERCEPTOR, IssueClassifier.classify(
                "ProductValidateInterceptor", "hybrisHTTP2", "InterceptorException", "InterceptorException", ""));
        assertEquals(IssueKind.MODEL_SAVE, IssueClassifier.classify(
                "DefaultModelService", "hybrisHTTP3", "could not save", "ModelSavingException", "modelService.save"));
    }

    @Test
    void titlesAreScannable() {
        StackFingerprint.Result fp = new StackFingerprint("com.yourcompany").compute(
                "NullPointerException",
                "\tat com.yourcompany.facades.impl.DefaultCartFacade.addToCart(DefaultCartFacade.java:142)\n"
        );
        String occ = IssueClassifier.title(
                IssueKind.OCC, "NullPointerException", "Failed to add product", "DefaultCartFacade",
                fp, Map.of());
        assertEquals("OCC DefaultCartFacade.addToCart — NPE", occ);

        String cron = IssueClassifier.title(
                IssueKind.CRONJOB, "ModelNotFoundException", "failed", "solrIncrementalUpdateCronJob",
                fp, Map.of("cronjob", "solrIncrementalUpdate"));
        assertTrue(cron.startsWith("CronJob solrIncrementalUpdate failed"));
        assertTrue(cron.contains("ModelNotFoundException"));
    }

    @Test
    void occTitleDoesNotRepeatOccWhenClassAlreadyStartsWithIt() {
        StackFingerprint.Result fp = new StackFingerprint("de.hybris").compute(
                "CartException",
                "\tat de.hybris.platform.commercewebservices.core.filter.OCCConsentLayerFilter.doFilterInternal(OCCConsentLayerFilter.java:88)\n"
        );
        String title = IssueClassifier.title(
                IssueKind.OCC, "CartException", "consent failed", "OCCConsentLayerFilter",
                fp, Map.of());
        assertEquals("OCCConsentLayerFilter.doFilterInternal — CartException", title);
        assertTrue(IssueClassifier.startsWithKindToken("OCCConsentLayerFilter.doFilterInternal", "OCC"));
        assertEquals("OCCConsentLayerFilter.doFilterInternal",
                IssueClassifier.withKindPrefix("OCC", "OCCConsentLayerFilter.doFilterInternal"));
        assertEquals("OCC DefaultCartFacade.addToCart",
                IssueClassifier.withKindPrefix("OCC", "DefaultCartFacade.addToCart"));
    }
}
