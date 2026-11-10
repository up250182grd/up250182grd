package com.englishos.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.LearningPlans;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Course;
import com.englishos.learning.domain.LearningPlan;
import com.englishos.learning.infrastructure.CourseRepository;
import com.englishos.learning.infrastructure.LearningPlanRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan personalizado (RF-ASM-5): cursos publicados desde el nivel de partida en adelante. Si no hay contenido de ese
 * nivel todavía, se asigna el curso publicado más avanzado disponible (repaso), y no se inventa contenido.
 */
@Service
class PlanService implements LearningPlans {

    private final CourseRepository courses;
    private final LearningPlanRepository plans;
    private final Json json;
    private final Clock clock;

    PlanService(CourseRepository courses, LearningPlanRepository plans, Json json, Clock clock) {
        this.courses = courses;
        this.plans = plans;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void createFromLevel(Long userId, CefrLevel startLevel, String source) {
        List<Course> published = courses.findByStatusOrderByLevelAscSortOrderAsc(ContentStatus.PUBLISHED);
        List<Course> selected = published.stream()
                .filter(c -> c.getLevel().compareTo(startLevel) >= 0)
                .toList();
        if (selected.isEmpty() && !published.isEmpty()) {
            selected = List.of(published.getLast());
        }
        plans.findActive(userId).forEach(LearningPlan::deactivate);
        plans.save(LearningPlan.create(userId, source, startLevel,
                json.writeValue(selected.stream().map(Course::getId).toList()), Instant.now(clock)));
    }
}
