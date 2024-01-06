import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
    {
        title: 'Easy Integrations',
        Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
        description: (
            <>
                Numerous implementations of abstractions, providing you with a variety of LLMs and
                embedding stores to choose from.
            </>
        ),
    },
    {
        title: 'Developer-Friendly',
        Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
        description: (
            <>
                Clean and simple <code>APIs</code> with simple and coherent layer of abstractions, designed to ensure
                that
                your code does not depend on concrete implementations such as LLM providers, embedding store providers,
                etc. This allows for easy swapping of components.
            </>
        ),
    },
    {
        title: 'Powered by Java',
        Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
        description: (
            <>
                Supercharge your Java application with the power of LLMs.
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
