package me.zhengjie.config.db;


import com.alibaba.druid.DbType;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import java.util.Map;


@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "entityManagerFactoryEladmin", // 配置连接工厂
        transactionManagerRef = "transactionManagerEladmin", // 配置事物管理器
        basePackages = {"me.zhengjie.repository", "me.zhengjie.modules.mnt.repository", "me.zhengjie.modules.quartz.repository", "me.zhengjie.modules.system.repository"} // 设置dao所在位置

)
public class DateSourceConfig4Eladmin {



    @Resource
    private HibernateProperties hibernateProperties;


    @Bean(name = "DataSource4Eladmin")
    @Primary
    @ConfigurationProperties("spring.datasource.druid.eladmin")
    public javax.sql.DataSource eladminDataSource() {
        DruidDataSource dataSource = DruidDataSourceBuilder.create().build();
        dataSource.setDbType(DbType.mariadb);
        System.out.println("eladmin:数据库dbtype：" + dataSource.getDbType());
        return dataSource;
    }


    @Bean(name = "entityManagerFactoryEladmin")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryEladmin(EntityManagerFactoryBuilder builder) {
//        Map<String, String> properties = jpaProperties.getProperties();
        Map<String, String> properties = new HashedMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQL5InnoDBDialect");
        return builder
                // 设置数据源
                .dataSource(eladminDataSource())
                //设置实体类所在位置.扫描所有带有 @Entity 注解的类
                .packages("me.zhengjie.domain", "me.zhengjie.modules.system.domain", "me.zhengjie.modules.quartz.domain", "me.zhengjie.modules.mnt.domain")
                .properties(hibernateProperties.determineHibernateProperties(properties, new HibernateSettings()))
                // Spring会将EntityManagerFactory注入到Repository之中.有了 EntityManagerFactory之后,
                // Repository就能用它来创建 EntityManager 了,然后 EntityManager 就可以针对数据库执行操作
                .persistenceUnit("PersistenceUnit4Eladmin")
                .build();

    }

    /**
     * 配置事物管理器
     *
     * @param builder
     * @return
     */
    @Bean(name = "transactionManagerEladmin")
    @Primary
    PlatformTransactionManager transactionManagerEladmin(EntityManagerFactoryBuilder builder) {
        return new JpaTransactionManager(entityManagerFactoryEladmin(builder).getObject());
    }


}
