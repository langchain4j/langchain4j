import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
    {
        title: 'Easy interaction with LLMs and AI',
        Svg: require('@site/static/img/llm-logos.svg').default,
        description: (
            <>
                All major commercial and open source models are easily accessible via a streamlined <code>API</code>, allowing you to build Chatbots, Assistants, Data Classifiers, Autonomous Agents, ...
            </>
        ),
    },
    {
        title: 'Tailored for Java',
        Svg: require('@site/static/img/framework-logos.svg').default,
        description: (
            <>
                Smooth integration in your java applications thanks to Quarkus and Spring Boot integrations, converse with LLMs in POJOs and have the LLM call Java methods
            </>
        ),
    },
    {
        title: 'Tools, AI Services, Chains, RAG',
        Svg: require('@site/static/img/functionality-logos.svg').default,
        description: (
            <>
                Provides an extensive toolbox of common AI LLM operations, made easy thanks to various layers of abstraction
            </>
        ),
    }
];

function Feature({Svg, title, description}) {
    return (
        <div className={clsx('col col--4')}>
            <div className="text--center">
                <Svg className={styles.featureSvg} role="img"/>
            </div>
            <div className="text--center padding-horiz--md">
                <Heading as="h3">{title}</Heading>
                <p>{description}</p>
            </div>
        </div>
    );
}

export default function HomepageFeatures() {
    return (
        <section className={styles.features}>
            <div className="container">
                <div className="row">
                    {FeatureList.map((props, idx) => (
                        <Feature key={idx} {...props} />
                    ))}
                </div>
            </div>
        </section>
    );
}
