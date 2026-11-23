import React, { type ReactNode } from 'react';
import styles from './styles.module.css';

export interface ProjectCardProps {
  title: string;
  stars?: string;
  description?: ReactNode;
  children?: ReactNode;
  url: string;
  badge?: string;
}

export default function ProjectCard({
  title,
  stars,
  description,
  children,
  url,
  badge,
}: ProjectCardProps): ReactNode {
  return (
    <div className="card">
      <div className="card__header">
        <div className={styles.headerRow}>
          <h3>{title}</h3>
          {badge && <span className={styles.badge}>{badge}</span>}
        </div>
      </div>
      <div className="card__body">
        {stars && <p className={styles.stars}>⭐ {stars}</p>}
        <p>{description ?? children}</p>
      </div>
      <div className="card__footer">
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="button button--primary button--block"
        >
          Visit Project
        </a>
      </div>
    </div>
  );
}
