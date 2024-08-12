package com.ru.scraper.service;

import com.ru.scraper.data.meal.MealOption;
import com.ru.scraper.data.response.MenuResult;
import com.ru.scraper.data.response.ResponseMenu;
import com.ru.scraper.exception.types.RuMenuNotFound;
import com.ru.scraper.factory.ResponseMenuBuilder;
import com.ru.scraper.helper.ScraperHelper;
import com.ru.scraper.helper.Utils;
import com.ru.scraper.scraper.ScraperRU;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScrapService implements IScrapService {

    private final ResponseMenuBuilder responseMenuBuilder;
    private final ScraperHelper scraperHelper;
    private final ScraperRU scraperRU;
    private final Utils utils;

    @Value("${RU_CODE}")
    private String ruKey;
    @Value("${RU_URL}")
    private String ruUrl;

    public ScrapService(ResponseMenuBuilder responseMenuBuilder, ScraperRU scraperRU, ScraperHelper scraperHelper, Utils utils) {
        this.responseMenuBuilder = responseMenuBuilder;
        this.scraperHelper = scraperHelper;
        this.scraperRU = scraperRU;
        this.utils = utils;
    }

    public ResponseMenu scrape() throws InterruptedException {
        Document documentFromUrl = scraperRU.connectScraper(ruUrl);
        String formattedDate = utils.getFormattedDate(LocalDateTime.now());
        MenuResult menuResult = scraperRU.parseTableHtml(documentFromUrl, formattedDate);

        if (menuResult == null) {
            throw new RuMenuNotFound("Menu not found with this date " + formattedDate);
        }

        System.out.println("Menu parsed for the restaurant " + ruKey);

        Map<String, List<MealOption>> meals = new HashMap<>();
        List<MealOption> mealOptions = new ArrayList<>();
        List<String> served = new ArrayList<>();
        String mealPeriodTitle = null;
        String imgMenu = null;

        if (menuResult.isImage()) {
            Element imgElement = menuResult.getImageElement();
            imgMenu = imgElement.attr("src");
            System.out.println("Menu is an image. URL: " + imgElement.attr("src"));
            return responseMenuBuilder.createResponseMenuWithImg(imgMenu);
        }

        Elements mealRows = menuResult.getTableRows();
        System.out.println("Meals found: " + mealRows);

        for (Element element : mealRows) {
            Element tdElement = element.select("td").first();
            String mealTitle = scraperHelper.translateMeal(tdElement.text());

            if (mealTitle != null) {
                scraperRU.updateMeals(meals, mealOptions, mealPeriodTitle);
                mealOptions = new ArrayList<>();
                mealPeriodTitle = mealTitle;
                served.add(mealTitle);
                continue;
            }

            scraperRU.processContentFromRow(tdElement.html(), mealOptions, scraperRU);
        }

        scraperRU.updateMeals(meals, mealOptions, mealPeriodTitle);
        return responseMenuBuilder.createResponseMenu(meals, served);
    }
}
