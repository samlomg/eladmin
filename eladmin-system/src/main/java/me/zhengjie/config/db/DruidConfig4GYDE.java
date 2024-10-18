package me.zhengjie.config.db;

import com.alibaba.druid.DbType;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Map;


/**
 * @description: 动态数据源
 */
@Slf4j
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "entityManagerFactoryForGYDE", // 配置连接工厂
        transactionManagerRef = "transactionManagerForGYDE", // 配置事物管理器
        basePackages = {"cn.shininghouse.gyde.repository"} // 设置dao所在位置

)
public class DruidConfig4GYDE {


    @Resource
    private HibernateProperties hibernateProperties;


    @Bean(name = "DataSource4GYDE")
    @ConfigurationProperties("spring.datasource.druid.gyde")
    public DataSource dataSource4GYDE() {
        DruidDataSource dataSource = DruidDataSourceBuilder.create().build();
        dataSource.setDbType(DbType.sqlserver);
        System.out.println("LBC数据库dbtype：" + dataSource.getDbType());
        return dataSource;
    }


    @Bean(name = "entityManagerFactoryForGYDE")
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryLbc(EntityManagerFactoryBuilder builder) {

        Map<String,String> jpaPro = new HashedMap<>();
        jpaPro.put("hibernate.dialect", "org.hibernate.dialect.SQLServer2005Dialect");
        jpaPro.put("spring.jpa.properties.dialect", "org.hibernate.dialect.SQLServer2005Dialect");
//        jpaPro.put("spring.jpa.hibernate.naming.implicit-strategy", "org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl");
//        jpaPro.put("spring.jpa.hibernate.naming.physical-strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        log.info("============GYDE Properites:{},JPA 设置正在启动====================", JSON.toJSONString(jpaPro));
        return builder
                // 设置数据源
                .dataSource(dataSource4GYDE())
                //设置实体类所在位置.扫描所有带有 @Entity 注解的类
                .packages("cn.shininghouse.gyde.domain")
                .properties(hibernateProperties.determineHibernateProperties(jpaPro, new HibernateSettings()))
                // Spring会将EntityManagerFactory注入到Repository之中.有了 EntityManagerFactory之后,
                // Repository就能用它来创建 EntityManager 了,然后 EntityManager 就可以针对数据库执行操作
                .persistenceUnit("PersistenceUnitForGYDE")
                .build();

    }

    /**
     * 配置事物管理器
     *
     * @param builder
     * @return
     */
    @Bean(name = "transactionManagerForGYDE")
    PlatformTransactionManager transactionManagerForGYDE(EntityManagerFactoryBuilder builder) {
        return new JpaTransactionManager(entityManagerFactoryLbc(builder).getObject());
    }

}





