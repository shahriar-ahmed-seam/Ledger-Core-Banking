import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Money } from '../components/ui';

describe('Money component', () => {
  it('renders USD with two decimal places and a symbol', () => {
    render(<Money amount="1234.50" currency="USD" />);
    expect(screen.getByText('$1,234.50')).toBeInTheDocument();
  });

  it('renders JPY with no decimal places', () => {
    render(<Money amount="5000" currency="JPY" />);
    // Japanese yen uses ¥ and zero fraction digits.
    expect(screen.getByText(/5,000/)).toBeInTheDocument();
    expect(screen.getByText(/5,000/).textContent).not.toContain('.');
  });
});
