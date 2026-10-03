import React, { type ReactNode } from 'react';

export interface ProjectCardGridProps {
  children: ReactNode;
}

export default function ProjectCardGrid({ children }: ProjectCardGridProps): ReactNode {
  return (
    <div className="row">
      {React.Children.map(children, (child) => (
        <div className="col col--4 margin-bottom--lg">{child}</div>
      ))}
    </div>
  );
}
