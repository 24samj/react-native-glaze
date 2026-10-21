import { render, screen, userEvent } from '@testing-library/react-native';
import { createRef } from 'react';
import { Animated, Platform, Text, View, type ViewProps } from 'react-native';

import { GlassButton, GlassContainer, GlassView } from '..';

// Native views render as plain host Views tagged with their name, so props are inspectable
jest.mock('expo', () => {
  const { forwardRef, createElement } = jest.requireActual<typeof import('react')>('react');
  const RN = jest.requireActual<typeof import('react-native')>('react-native');
  return {
    requireNativeView: (_module: string, view: string) =>
      forwardRef<View, ViewProps>((props, ref) =>
        createElement(RN.View, { ...props, ref, testID: props.testID ?? view })
      ),
    requireNativeModule: () => ({ isLiquidGlassAvailable: () => true }),
  };
});

let mockNativeGlass = true;
jest.mock('expo-glass-effect', () => {
  const { forwardRef, createElement } = jest.requireActual<typeof import('react')>('react');
  const RN = jest.requireActual<typeof import('react-native')>('react-native');
  const make = (name: string) =>
    forwardRef<View, ViewProps>((props, ref) =>
      createElement(RN.View, { ...props, ref, testID: props.testID ?? name })
    );
  return {
    GlassView: make('ExpoGlassView'),
    GlassContainer: make('ExpoGlassContainer'),
    isLiquidGlassAvailable: () => mockNativeGlass,
    isGlassEffectAPIAvailable: () => mockNativeGlass,
  };
});

jest.mock('expo-blur', () => {
  const { createElement } = jest.requireActual<typeof import('react')>('react');
  const RN = jest.requireActual<typeof import('react-native')>('react-native');
  return {
    BlurView: (props: ViewProps) => createElement(RN.View, { ...props, testID: 'BlurView' }),
  };
});

const native = Platform.OS === 'ios' || Platform.OS === 'android';

beforeEach(() => {
  mockNativeGlass = true;
});

describe('GlassView', () => {
  it('renders children', async () => {
    await render(
      <GlassView>
        <Text>inside</Text>
      </GlassView>
    );
    expect(screen.getByText('inside')).toBeTruthy();
  });

  it('forwards the ref to a native view', async () => {
    const ref = createRef<View>();
    await render(<GlassView ref={ref} />);
    expect(ref.current).not.toBeNull();
  });

  it('passes style through untouched', async () => {
    const style = { width: 120, height: 40, borderRadius: 20 };
    await render(<GlassView testID="glass" style={style} />);
    expect(screen.getByTestId('glass').props.style).toEqual(style);
  });

  it('can be wrapped with Animated.createAnimatedComponent', async () => {
    const AnimatedGlass = Animated.createAnimatedComponent(GlassView);
    await render(
      <AnimatedGlass style={{ opacity: new Animated.Value(1) }}>
        <Text>animated</Text>
      </AnimatedGlass>
    );
    expect(screen.getByText('animated')).toBeTruthy();
  });

  if (Platform.OS === 'android') {
    it('maps props to the native Glaze view', async () => {
      await render(
        <GlassView
          testID="glass"
          glassEffectStyle={{ style: 'clear', animate: true, animationDuration: 0.5 }}
          tintColor="#ff0000"
          isInteractive
          colorScheme="dark"
          style={{ borderRadius: 12, borderTopLeftRadius: 4 }}
        />
      );
      const props = screen.getByTestId('glass').props;
      expect(props.glassStyle).toBe('clear');
      expect(props.styleAnimation).toEqual([1, 0.5]);
      expect(props.glassTint).toBe(0xffff0000 | 0);
      expect(props.interactive).toBe(true);
      expect(props.glassColorScheme).toBe('dark');
      expect(props.cornerRadii).toEqual([4, 12, 12, 12]);
    });

    it('uses defaults matching expo-glass-effect', async () => {
      await render(<GlassView testID="glass" />);
      const props = screen.getByTestId('glass').props;
      expect(props.glassStyle).toBe('regular');
      expect(props.styleAnimation).toEqual([0, -1]);
      expect(props.interactive).toBe(false);
      expect(props.glassColorScheme).toBe('auto');
      expect(props.cornerRadii).toEqual([0, 0, 0, 0]);
    });
  }

  if (Platform.OS === 'ios') {
    it('renders Apple glass with props forwarded when available', async () => {
      await render(<GlassView glassEffectStyle="clear" tintColor="red" isInteractive />);
      const props = screen.getByTestId('ExpoGlassView').props;
      expect(props.glassEffectStyle).toBe('clear');
      expect(props.tintColor).toBe('red');
      expect(props.isInteractive).toBe(true);
    });

    it('falls back to a blur material below iOS 26', async () => {
      mockNativeGlass = false;
      await render(<GlassView glassEffectStyle="regular" />);
      expect(screen.queryByTestId('ExpoGlassView')).toBeNull();
      expect(screen.getByTestId('BlurView').props.tint).toBe('systemMaterialLight');
    });

    it("renders no material for 'none' in the fallback", async () => {
      mockNativeGlass = false;
      await render(<GlassView glassEffectStyle="none" />);
      expect(screen.queryByTestId('BlurView')).toBeNull();
    });
  }
});

describe('GlassButton', () => {
  it('fires onPress', async () => {
    const onPress = jest.fn();
    await render(
      <GlassButton onPress={onPress}>
        <Text>tap</Text>
      </GlassButton>
    );
    await userEvent.setup().press(screen.getByText('tap'));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('defaults to a button role and reports disabled', async () => {
    await render(
      <GlassButton disabled onPress={() => {}}>
        <Text>off</Text>
      </GlassButton>
    );
    const button = screen.getByRole('button');
    expect(button.props.accessibilityState).toEqual(expect.objectContaining({ disabled: true }));
  });

  it('does not fire onPress when disabled', async () => {
    const onPress = jest.fn();
    await render(
      <GlassButton disabled onPress={onPress}>
        <Text>off</Text>
      </GlassButton>
    );
    await userEvent.setup().press(screen.getByText('off'));
    expect(onPress).not.toHaveBeenCalled();
  });

  it('forwards accessibility labels and the ref', async () => {
    const ref = createRef<View>();
    await render(<GlassButton ref={ref} accessibilityLabel="Save" />);
    expect(screen.getByLabelText('Save')).toBeTruthy();
    expect(ref.current).not.toBeNull();
  });

  if (native) {
    it('is a pill by default and interactive unless disabled', async () => {
      await render(<GlassButton style={{ height: 50 }} />);
      const glass = screen.getByTestId(Platform.OS === 'ios' ? 'ExpoGlassView' : 'GlazeView');
      const style = Object.assign({}, ...([glass.props.style].flat(3).filter(Boolean) as object[]));
      expect(style.borderRadius).toBe(25);
      expect(glass.props[Platform.OS === 'ios' ? 'isInteractive' : 'interactive']).toBe(true);
    });

    it('keeps an explicit radius and puts layout styles on the Pressable', async () => {
      await render(<GlassButton style={{ borderRadius: 8, margin: 10 }} />);
      const glass = screen.getByTestId(Platform.OS === 'ios' ? 'ExpoGlassView' : 'GlazeView');
      const style = Object.assign({}, ...([glass.props.style].flat(3).filter(Boolean) as object[]));
      expect(style.borderRadius).toBe(8);
      expect(style.margin).toBeUndefined();
      const outer = Object.assign(
        {},
        ...([screen.getByRole('button').props.style].flat(3).filter(Boolean) as object[])
      );
      expect(outer.margin).toBe(10);
      expect(outer.minHeight).toBe(44);
    });
  }
});

describe('GlassContainer', () => {
  it('renders children and forwards spacing on native', async () => {
    await render(
      <GlassContainer spacing={8} testID="container">
        <Text>child</Text>
      </GlassContainer>
    );
    expect(screen.getByText('child')).toBeTruthy();
    if (native) expect(screen.getByTestId('container').props.spacing).toBe(8);
  });
});
