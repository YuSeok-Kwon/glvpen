package com.kepg.glvpen.modules.analysis.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;
import com.kepg.glvpen.modules.analysis.service.AnalysisService;
import com.kepg.glvpen.modules.analysis.service.ColumnService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ColumnService columnService;

    /**
     * 매거진 메인 페이지 (피처드 + 컬럼 그리드 + 카테고리 필터)
     */
    @GetMapping
    public String magazine(@RequestParam(required = false) String category,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "9") int size,
                           Model model) {
        AnalysisColumn featured = columnService.getFeaturedColumn();
        Page<AnalysisColumn> columns = columnService.getColumns(category, page, size);
        model.addAttribute("featured", featured);
        model.addAttribute("columns", columns);
        model.addAttribute("category", category);
        return "analysis/magazine";
    }

    /**
     * 하위호환: /analysis/dashboard → /analysis 리다이렉트
     */
    @GetMapping("/dashboard")
    public String dashboardRedirect() {
        return "redirect:/analysis";
    }

    /**
     * 하위호환: /analysis/columns → /analysis 리다이렉트
     */
    @GetMapping("/columns")
    public String columnsRedirect(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return "redirect:/analysis?category=" + category;
        }
        return "redirect:/analysis";
    }

    @GetMapping("/player/{id}")
    public String playerAnalysis(@PathVariable int id,
                                  @RequestParam(defaultValue = "WAR") String category,
                                  @RequestParam(defaultValue = "2020") int startYear,
                                  @RequestParam(required = false) Integer endYear,
                                  Model model) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        var playerData = analysisService.getPlayerTrend(id, category, startYear, validEndYear);
        model.addAttribute("playerData", playerData);
        return "analysis/player-analysis";
    }

    @GetMapping("/team/{id}")
    public String teamAnalysis(@PathVariable int id,
                                @RequestParam(required = false) Integer season,
                                Model model) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        var teamData = analysisService.getTeamBalance(id, validSeason);
        model.addAttribute("teamData", teamData);
        return "analysis/team-analysis";
    }

    /**
     * 컬럼 상세 (이전/다음 네비게이션 + 관련 컬럼)
     */
    @GetMapping("/columns/{id}")
    public String columnDetail(@PathVariable Long id, Model model) {
        AnalysisColumn column = columnService.getColumnDetail(id);
        model.addAttribute("column", column);
        model.addAttribute("prevColumn", columnService.getPreviousColumn(id).orElse(null));
        model.addAttribute("nextColumn", columnService.getNextColumn(id).orElse(null));
        List<AnalysisColumn> relatedColumns = columnService.getRelatedColumns(column);
        model.addAttribute("relatedColumns", relatedColumns);
        return "analysis/column-detail";
    }

    @GetMapping("/columns/write")
    public String columnWrite(Model model) {
        return "analysis/column-write";
    }

    @GetMapping("/season-trend")
    public String seasonTrend(Model model) {
        return "analysis/season-trend";
    }

    @GetMapping("/advanced")
    public String advancedAnalysis(Model model) {
        return "analysis/advanced-analysis";
    }
}
