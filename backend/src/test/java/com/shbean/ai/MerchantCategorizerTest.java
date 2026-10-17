package com.shbean.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shbean.ai.MerchantCategorizer.CategoryAnswer;

class MerchantCategorizerTest {

    private final AiCache cache = mock(AiCache.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final MerchantCategorizer categorizer = new MerchantCategorizer(cache, ai);

    @Test
    void canonicalNessieCategoryWinsWithoutLookups() {
        assertThat(categorizer.categorize("FreshMart", "groceries")).isEqualTo("Groceries");
        verify(cache, never()).merchantCategory(anyString());
    }

    @Test
    void keywordRuleIsCachedWithoutCallingAi() {
        when(cache.merchantCategory("joe's pizza")).thenReturn(Optional.empty());

        assertThat(categorizer.categorize("Joe's Pizza!", "Food")).isEqualTo("Dining");
        verify(cache).putMerchantCategory("joe's pizza", "Dining", "RULES");
        verify(ai, never()).entity(anyString(), anyString(), eq(CategoryAnswer.class));
    }

    @Test
    void aiAnswerMustBeCanonicalToBeUsedAndCached() {
        when(cache.merchantCategory(anyString())).thenReturn(Optional.empty());
        when(ai.entity(anyString(), anyString(), eq(CategoryAnswer.class)))
                .thenReturn(Optional.of(new CategoryAnswer("travel")))
                .thenReturn(Optional.of(new CategoryAnswer("Crypto")));

        assertThat(categorizer.categorize("Zephyr Holdings", null)).isEqualTo("Travel");
        verify(cache).putMerchantCategory("zephyr holdings", "Travel", "AI");
        assertThat(categorizer.categorize("Quasar LLC", null)).isEqualTo(MerchantCategorizer.UNCATEGORIZED);
    }
}
