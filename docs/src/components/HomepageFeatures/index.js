import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
    {
        title: 'Easy interaction with LLMs and Vector Stores',
        Svg: require('@site/static/img/llm-logos.svg').default,
        description: (
            <>
                All major commercial and open-source LLMs and Vector Stores are easily accessible through a unified API, enabling you to build chatbots, assistants and more.
            </>
        ),
    },
    {
        title: 'Tailored for Java',
        Svg: require('@site/static/img/framework-logos.svg').default,
        description: (
            <>
                Smooth integration into your Java applications is made possible thanks to Quarkus and Spring Boot integrations. There is two-way integration between LLMs and Java: you can call LLMs from Java and allow LLMs to call your Java code in return.
            </>
        ),
    },
    {
        title: 'AI Services, RAG, Tools',
        Svg: require('@site/static/img/functionality-logos.svg').default,
        description: (
            <>
                Our extensive toolbox provides a wide range of tools for common LLM operations, from low-level prompt templating, chat memory management, and output parsing, to high-level patterns like AI Services and RAG.
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
