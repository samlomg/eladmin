package me.zhengjie.config.db;


import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
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
import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "entityManagerFactoryLbc", // 配置连接工厂
        transactionManagerRef = "transactionManagerLbc", // 配置事物管理器
        basePackages = {"cn.shininghouse.lbc.repository"} // 设置dao所在位置
)
public class DateSourceConfig4Lbc {


    @Resource
    private HibernateProperties hibernateProperties;


    @Bean(name = "DataSource4Lbc")
    @ConfigurationProperties("spring.datasource.druid.lbc")
    public DataSource lbcDataSource() {
        DruidDataSource dataSource = DruidDataSourceBuilder.create().build();
        System.out.println("LBC数据库dbtype：" + dataSource.getDbType());
        return dataSource;
    }


    @Bean(name = "entityManagerFactoryLbc")
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryLbc(EntityManagerFactoryBuilder builder) {
        Map<String, String> properties = new HashedMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.SQLServer2005Dialect");
        properties.put("spring.jpa.properties.dialect", "org.hibernate.dialect.SQLServer2005Dialect");
        return builder
                // 设置数据源
                .dataSource(lbcDataSource())
                // 设置实体类所在位置.扫描所有带有 @Entity 注解的类
                .packages("cn.shininghouse.lbc.domain")
                .properties(hibernateProperties.determineHibernateProperties(properties, new HibernateSettings()))
                // Spring会将EntityManagerFactory注入到Repository之中.有了 EntityManagerFactory之后,
                // Repository就能用它来创建 EntityManager 了,然后 EntityManager 就可以针对数据库执行操作
                .persistenceUnit("PersistenceUnit4Lbc")
                .build();
    }

    /**
     * 配置事物管理器
     *
     * @param builder
     * @return
     */
    @Bean(name = "transactionManagerLbc")
    PlatformTransactionManager transactionManagerLbc(EntityManagerFactoryBuilder builder) {
        return new JpaTransactionManager(entityManagerFactoryLbc(builder).getObject());
    }


}
