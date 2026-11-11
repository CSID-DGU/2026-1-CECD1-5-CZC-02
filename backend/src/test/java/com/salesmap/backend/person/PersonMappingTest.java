package com.salesmap.backend.person;

import com.salesmap.backend.user.entity.User;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** MySQL에 연결하지 않고 엔티티·소유자 FK·실제 검색 HQL의 매핑을 검사한다. */
class PersonMappingTest {
    @Test void entityAndOwnerScopedQueryCompileWithoutDatabase() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(PersonEntry.class).addAnnotatedClass(IdentityResolutionEntry.class)
                .addAnnotatedClass(User.class).buildMetadata().buildSessionFactory();
             var session = factory.openSession()) {
            var query = session.createQuery(PersonRepository.SEARCH, PersonEntry.class);
            assertThat(query.getParameters()).extracting(parameter -> parameter.getName()).containsExactlyInAnyOrder("owner","pattern");
            query.setParameter("owner",7L).setParameter("pattern","%가상%");
            session.createQuery(IdentityResolutionRepository.LOCK_OWNED,IdentityResolutionEntry.class);
            // execute/getResultList는 호출하지 않으므로 외부 DB에 접근하지 않는다.
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
